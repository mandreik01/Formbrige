package pl.formbridge.core.analysis;

import pl.formbridge.core.StandardSections.ApplicantMode;
import pl.formbridge.core.TechNames;
import pl.formbridge.core.model.Condition;
import pl.formbridge.core.model.FieldSpec;
import pl.formbridge.core.model.FieldSpec.Type;
import pl.formbridge.core.model.PageSpec;
import pl.formbridge.core.model.SectionSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormAnalyzer {

    private static final Logger LOG = Logger.getLogger(FormAnalyzer.class.getName());

    public record Analysis(String title, String office, boolean organ, ApplicantMode mode,
                           List<PageSpec> pages, List<FieldSpec.Consent> statements,
                           List<String> proposedDictionaries) {
    }

    private static final Pattern TITLE = Pattern.compile("(?iU)^(wniosek|deklaracja|zgłoszenie|oświadczenie|podanie|informacja o)\\b.*");
    private static final Pattern OFFICE = Pattern.compile(
            "(?U)^(Urząd|URZĄD|Wójt|WÓJT|Burmistrz|BURMISTRZ|Prezydent|PREZYDENT|Starosta|STAROSTA|Marszałek|MARSZAŁEK|"
                    + "Starostwo|STAROSTWO|Rada|RADA|Zarząd|ZARZĄD|Dyrektor|DYREKTOR)\\b.*");
    private static final Pattern ORGAN = Pattern.compile("(?iU)^(wójt|burmistrz|prezydent|starosta|marszałek|rada|zarząd|dyrektor)\\b.*");
    private static final Pattern STOP = Pattern.compile("(?iU)^(klauzula|informacj[ae] dotycząc[ae] (przetwarzania|ochrony)|"
            + "zgodnie z art\\.? ?13|administratorem (pani|pana|państwa|twoich)|informacje:|pouczenie\\b|obowiązek informacyjny).*");
    private static final Pattern NOISE = Pattern.compile("(?iU)^(strona \\d.*|str\\. ?\\d.*|\\d+\\s*/\\s*\\d+|[-`',:;\\s]+|\\.{1,3}|dd-mm-rrrr|"
            + "\\d+[.)]?|(\\p{Lu} ?){1,4}|wersja .*|miejsce na pieczęć.*|/?wypełnia (organ|urząd)/?|nr sprawy.*|\\*+ ?niepotrzebne skreślić.*|"
            + "/\\*.*|/\\*\\*.*|data wpływu.*|pieczęć.*|załącznik nr \\d+ do .*|nr \\d+/.*|z dnia \\d.*)$");
    private static final Pattern SIGNATURE = Pattern.compile("(?iU)^[(/]?\\s*(czytelny )?podpis.*|^data i podpis.*|^\\(?miejscowość i data\\)?$|"
            + "^.{0,30}, ?dnia\\s*$|^data\\s*$");
    private static final Pattern SKIP_SECTION = Pattern.compile("(?iU).*(instrukcja wypełnienia|dane wnioskodawcy|dane identyfikacyjne wnioskodawcy|"
            + "dane identyfikacyjne$|dane kontaktowe|dane pełnomocnika|adres siedziby lub miejsca|data i podpis|podstawa prawna|"
            + "organ, do którego|miejsce składania|termin składania|objaśnienia|adnotacje urzędu|wypełnia urząd|wypełnia organ|"
            + "wypełnij wielkimi|wypełniać wielkimi|pola wyboru oznacz|wypełnia wnioskodawca|pouczenie|informacja o przetwarzaniu).*");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d{1,2}(?:\\.\\d{1,2})*)[.)]?\\s+(.+)$");
    private static final Pattern LETTERED = Pattern.compile("^([A-H])(\\.\\d{1,2})?\\.?\\s+(\\p{Lu}.+)$");
    private static final Pattern SUBLETTER = Pattern.compile("^([a-h])[/)]\\s+(.+)$");
    private static final Pattern ROMAN = Pattern.compile("^(I{1,3}|IV|V|VI{0,3})\\.\\s*(.*)$");
    private static final Pattern ITEM = Pattern.compile("^(\\d{1,2})[.)]\\s*(.+)$");
    private static final Pattern ALTERNATIVE = Pattern.compile(
            "(?U)(\\p{L}+(?:/\\p{L}+)+)\\s?\\*(?!\\*)");
    private static final Pattern APPLICANT_FIELD = Pattern.compile("(?iU)^(imię|nazwisko|imię i nazwisko.*|adres( zamieszkania.*)?|"
            + "(nr|numer) telefonu.*|telefon.*|e-mail|adres e-mail|pesel|data|podpis.*)$");
    private static final Pattern ORG_HINTS = Pattern.compile("(?iU)\\b(nip|regon|krs|nazwa firmy|nazwa wnioskodawcy|siedzib\\w*|"
            + "osob[ay] prawn\\w*|jednostk\\w+ organizacyjn\\w*|nazwa /imię|nazwa/imię|klub\\w*|przedsiębior\\w*|podmiot\\w*)\\b");

    private final TechNames.Registry names;

    public FormAnalyzer(TechNames.Registry names) {
        this.names = names;
    }

    public Analysis analyze(String fileName, String text) {
        List<String> lines = TextLines.normalize(text);
        int titleIdx = findTitle(lines);
        int titleEnd = titleIdx < 0 ? 0 : titleEnd(lines, titleIdx);
        String textTitle = titleIdx < 0 ? null : cleanTitle(String.join(" ", lines.subList(titleIdx, titleEnd)));
        String title = chooseTitle(textTitle, fileName);

        String[] office = findOffice(lines, titleIdx < 0 ? Math.min(lines.size(), 30) : titleIdx + 12);
        ApplicantMode mode = ORG_HINTS.matcher(text).find() ? ApplicantMode.ALL : ApplicantMode.PERSON;

        int stop = lines.size();
        for (int i = Math.max(titleEnd, 0); i < lines.size(); i++) {
            if (STOP.matcher(lines.get(i)).matches()) {
                stop = i;
                break;
            }
        }
        List<String> body = new ArrayList<>(lines.subList(Math.min(titleEnd, stop), stop));
        long allLeaders = lines.stream().filter(TextLines::hasLeader).count();
        Body parsed = new BodyParser(body, allLeaders >= 4).parse();

        String finalTitle = parsed.groupNames.size() >= 2 ? combinedTitle(parsed.groupNames, title) : title;
        List<PageSpec> pages = paginate(parsed);
        return new Analysis(finalTitle, office[0], Boolean.parseBoolean(office[1]), mode, pages,
                parsed.statements, parsed.proposedDictionaries);
    }


    private static int findTitle(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (TITLE.matcher(l).matches() && !TextLines.hasLeader(l) && l.length() < 200
                    && !l.toLowerCase(Locale.ROOT).startsWith("wniosek należy")
                    && !l.toLowerCase(Locale.ROOT).startsWith("informacja o przetwarzaniu")) {
                return i;
            }
        }
        return -1;
    }

    private static int titleEnd(List<String> lines, int idx) {
        int end = idx + 1;
        String first = lines.get(idx);
        boolean upper = first.equals(first.toUpperCase(Locale.ROOT));
        while (end < lines.size() && end - idx < 4) {
            String next = lines.get(end);
            boolean shortLine = next.length() < 90 && !TextLines.hasLeader(next) && !NUMBERED.matcher(next).matches()
                    && !next.toLowerCase(Locale.ROOT).startsWith("proszę") && !next.toLowerCase(Locale.ROOT).startsWith("wnoszę")
                    && !next.matches("(?iU)^(na podstawie|zgodnie|działając|w związku|\\(dz|stosownie|uprzejmie).*");
            boolean continues = first.split("\\s+").length <= 2
                    || (upper && next.equals(next.toUpperCase(Locale.ROOT)) && next.length() > 1)
                    || Character.isLowerCase(next.codePointAt(0));
            if (!shortLine || !continues || TITLE.matcher(next).matches()) {
                break;
            }
            end++;
        }
        return end;
    }

    static String cleanTitle(String t) {
        String s = t.replaceAll("\\*+", "").replaceAll("\\s+", " ").strip();
        s = s.replaceAll("(?U)(\\p{L}+)/(\\p{L}+)", "$1 lub $2");
        if (s.equals(s.toUpperCase(Locale.ROOT))) {
            s = s.toLowerCase(Locale.ROOT);
        } else {
            java.util.regex.Matcher lead = Pattern.compile("^(\\p{Lu}{3,})(\\s.*)$").matcher(s);
            if (lead.matches()) {
                s = lead.group(1).toLowerCase(Locale.ROOT) + lead.group(2);
            }
        }
        s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    private static String chooseTitle(String textTitle, String fileName) {
        String fromFile = null;
        if (fileName != null) {
            String base = fileName.replaceAll("\\.[A-Za-z]{2,4}$", "").replaceAll("\\s*\\(\\d+\\)$", "").strip();
            if (TITLE.matcher(base).matches() && base.length() > 12) {
                fromFile = cleanTitle(base);
            }
        }
        if (textTitle == null || textTitle.length() < 8) {
            return fromFile != null ? fromFile : "Wniosek";
        }
        if (fromFile != null && fromFile.length() > textTitle.length() + 4
                && fromFile.toLowerCase(Locale.ROOT).startsWith(textTitle.toLowerCase(Locale.ROOT))) {
            return fromFile;
        }
        return textTitle;
    }

    private static String combinedTitle(List<String> groups, String fallback) {
        List<String[]> words = groups.stream().map(g -> cleanTitle(g).split(" ")).toList();
        String[] first = words.getFirst();
        int prefix = 0;
        outer:
        while (true) {
            for (String[] w : words) {
                if (prefix >= w.length || !w[prefix].equals(first[prefix])) {
                    break outer;
                }
            }
            prefix++;
        }
        int suffix = 0;
        outer2:
        while (true) {
            for (String[] w : words) {
                if (suffix >= w.length - prefix || !w[w.length - 1 - suffix].equals(first[first.length - 1 - suffix])) {
                    break outer2;
                }
            }
            suffix++;
        }
        if (prefix == 0) {
            return fallback;
        }
        List<String> middles = new ArrayList<>();
        for (String[] w : words) {
            middles.add(String.join(" ", java.util.Arrays.copyOfRange(w, prefix, w.length - suffix)));
        }
        String head = String.join(" ", java.util.Arrays.copyOfRange(first, 0, prefix));
        String tail = String.join(" ", java.util.Arrays.copyOfRange(first, first.length - suffix, first.length));
        String mid = middles.size() == 1 ? middles.getFirst()
                : String.join(", ", middles.subList(0, middles.size() - 1)) + " lub " + middles.getLast();
        String t = (head + " " + mid + " " + tail).replaceAll("\\s+", " ").strip();
        return t.length() > 255 ? fallback : t;
    }

    private static String[] findOffice(List<String> lines, int limit) {
        for (int i = 0; i < Math.min(limit, lines.size()); i++) {
            String l = lines.get(i);
            if (OFFICE.matcher(l).matches() && !TextLines.hasLeader(l) && l.length() < 90 && !l.contains("Zgodnie")) {
                String name = titleCase(l);
                if (i + 1 < lines.size()) {
                    String next = lines.get(i + 1);
                    String low = next.toLowerCase(Locale.ROOT);
                    boolean cont = next.length() < 60 && !next.matches(".*\\d.*")
                            && !low.matches("^(ul\\.|ul |ulica|al\\.|aleja|pl\\.|os\\.).*") && !TITLE.matcher(next).matches()
                            && !TextLines.hasLeader(next) && !NUMBERED.matcher(next).matches();
                    if (cont && next.toLowerCase(Locale.ROOT).startsWith("wydział")) {
                        name = name + " – " + titleCase(next);
                    } else if (cont && name.split(" ").length <= 4 && name.matches("(?iU).*\\b(gminy|miasta|powiatu|i gminy)$")) {
                        name = name + " " + titleCase(next);
                    }
                }
                return new String[]{name, String.valueOf(ORGAN.matcher(name).matches())};
            }
        }
        for (String l : lines) {
            Matcher m = Pattern.compile("(?U)\\b(Gminy|Miasta|Powiatu) (\\p{Lu}[\\p{L}-]+)").matcher(l);
            if (m.find()) {
                return new String[]{"Urząd " + m.group(1) + " " + m.group(2), "false"};
            }
        }
        return new String[]{"Urząd", "false"};
    }

    static String titleCase(String s) {
        if (!s.equals(s.toUpperCase(Locale.ROOT))) {
            return s.strip();
        }
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) {
                continue;
            }
            if (List.of("i", "w", "z", "na", "do", "ds.").contains(w)) {
                b.append(w);
            } else {
                b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            b.append(' ');
        }
        return b.toString().strip();
    }


    private static final class Heading {
        final String title;
        final int level;
        final String group;

        Heading(String title, int level, String group) {
            this.title = title;
            this.level = level;
            this.group = group;
        }
    }

    private static final class Body {
        final List<SectionSpec> sections = new ArrayList<>();
        final Map<SectionSpec, Integer> levels = new LinkedHashMap<>();
        final Map<SectionSpec, String> groups = new LinkedHashMap<>();
        final List<String> groupNames = new ArrayList<>();
        final List<FieldSpec.Consent> statements = new ArrayList<>();
        final List<String> proposedDictionaries = new ArrayList<>();
        final List<String> feeLines = new ArrayList<>();
    }

    private final class BodyParser {
        private final List<String> lines;
        private final Body body = new Body();
        private final boolean leaderDocument;
        private SectionSpec current;
        private boolean skipping;
        private int skipLevel;
        private boolean inStatements;
        private boolean inAttachments;
        private boolean optionsWithoutGlyphs;
        private boolean numberedOptions;
        private boolean attachmentsOptional;
        private boolean otherAttachmentAdded;
        private boolean skipNext;
        private String pendingLabel;
        private String currentGroup;
        private FieldSpec lastField;
        private FieldSpec optionGroup;
        private Condition conditionalOn;

        BodyParser(List<String> lines, boolean leaderDocument) {
            this.lines = lines;
            this.leaderDocument = leaderDocument;
        }

        Body parse() {
            detectGroups();
            for (int i = 0; i < lines.size(); i++) {
                try {
                    handle(i);
                } catch (RuntimeException e) {
                    // jedna nietypowa linia nie może przerwać konwersji całego wniosku
                    int line = i;
                    LOG.log(Level.FINE, e, () -> "Pominięto linię " + (line + 1) + ": " + lines.get(line));
                }
            }
            closeOptionGroup();
            finishSections();
            return body;
        }

        private void detectGroups() {
            for (int i = 0; i < lines.size(); i++) {
                Matcher r = ROMAN.matcher(lines.get(i));
                if (r.matches()) {
                    String name = r.group(2).isBlank() && i + 1 < lines.size() ? lines.get(i + 1) : r.group(2);
                    if (TITLE.matcher(name).matches()) {
                        String clean = cleanTitle(name);
                        String low = clean.toLowerCase(Locale.ROOT);
                        if (body.groupNames.stream().noneMatch(g -> g.toLowerCase(Locale.ROOT).startsWith(low))) {
                            body.groupNames.removeIf(g -> low.startsWith(g.toLowerCase(Locale.ROOT)));
                            body.groupNames.add(clean);
                        }
                    }
                }
            }
            if (body.groupNames.size() < 2) {
                body.groupNames.clear();
            }
        }

        private static boolean sharesPrefix(String text, String group) {
            String g = group.toLowerCase(Locale.ROOT);
            return text.toLowerCase(Locale.ROOT).startsWith(g.substring(0, Math.min(25, g.length())));
        }

        private void handle(int i) {
            String line = lines.get(i);
            String next = i + 1 < lines.size() ? lines.get(i + 1) : "";

            if (!body.groupNames.isEmpty()) {
                Matcher r = ROMAN.matcher(line);
                if (r.matches()) {
                    String name = r.group(2).isBlank() ? next : r.group(2);
                    String clean = TITLE.matcher(name).matches() ? cleanTitle(name) : null;
                    if (clean != null) {
                        String match = body.groupNames.stream()
                                .filter(g -> g.equalsIgnoreCase(clean) || sharesPrefix(clean, g))
                                .findFirst().orElse(null);
                        boolean tableOfContents = currentGroup == null && lines.subList(i + 1, Math.min(lines.size(), i + 6))
                                .stream().allMatch(l -> ROMAN.matcher(l).matches() || TITLE.matcher(l).matches());
                        if (match != null && !tableOfContents) {
                            closeOptionGroup();
                            currentGroup = match;
                            current = null;
                            pendingLabel = null;
                            skipping = false;
                        }
                    }
                    return;
                }
                if (TITLE.matcher(line).matches() && body.groupNames.stream().anyMatch(g -> sharesPrefix(cleanTitle(line), g))) {
                    return;
                }
            }

            if (skipNext) {
                skipNext = false;
                return;
            }
            if (line.matches("^\\*+\\s*$") || line.matches("^/?\\*{1,2}\\s*\\p{L}.*") && line.length() < 120
                    && line.toLowerCase(Locale.ROOT).matches("(?U).*(niepotrzebne|opcjonaln|wpisują|należy|dotyczy).*")) {
                closeOptionGroup();
                inAttachments = false;
                return;
            }
            if (NOISE.matcher(line).matches() || SIGNATURE.matcher(line).matches()) {
                return;
            }
            String lowLine = line.toLowerCase(Locale.ROOT);
            if (!skipping && !TextLines.hasLeader(line) && line.length() < 200
                    && ((lowLine.contains("opłat") && lowLine.matches("(?U).*\\d.*zł.*"))
                    || (lowLine.matches("(?U).*(numer konta|nr konta|rachunek bankowy|numer rachunku|nr rachunku).*")
                    && line.replaceAll("\\D", "").length() >= 20))) {
                body.feeLines.add(line.strip());
                return;
            }
            if (skipping && line.split("\\s+").length > 12 && lowLine.matches("(?U).*\\b(proszę o|wnoszę o|wnioskuję o)\\b.*")) {
                skipping = false;
                current = null;
            }
            if (lowLine.matches("(?U)^(oświadczenia?|oświadczam, że)\\s*:?$")) {
                closeOptionGroup();
                inStatements = true;
                inAttachments = false;
                return;
            }
            if (lowLine.equals("tak") && next.equalsIgnoreCase("nie")) {
                closeOptionGroup();
                if (!skipping) {
                    String label = pendingLabel != null ? pendingLabel : (current != null ? current.title : "Wybierz");
                    ensureSection();
                    addField(FieldSpec.of(Type.RADIO, capitalize(label), names.unique(label)).options(List.of("Tak", "Nie")).required());
                    pendingLabel = null;
                }
                skipNext = true;
                return;
            }

            if (numberedOptions && optionGroup != null) {
                Matcher item = ITEM.matcher(line);
                if (item.matches()) {
                    addOption(item.group(2));
                    return;
                }
                if (TextLines.isLeaderOnly(line)) {
                    return;
                }
                numberedOptions = false;
                closeOptionGroup();
            }

            if (line.length() < 90 && !TextLines.hasLeader(line)
                    && line.matches("(?iU)^(w załączeniu|załączniki|załączam|do wniosku (należy )?(dołączam|załączam|dołączyć)|wykaz załączników)\\b.*")) {
                closeOptionGroup();
                inAttachments = true;
                inStatements = false;
                skipping = false;
                attachmentsOptional = line.toLowerCase(Locale.ROOT).matches("(?U).*(postaw|zaznacz).*");
                startSection("Załączniki", 2);
                return;
            }

            Heading h = heading(line, next);
            if (h != null) {
                closeOptionGroup();
                if (skipping && h.level > skipLevel) {
                    return;
                }
                skipping = SKIP_SECTION.matcher(h.title).matches();
                if (skipping) {
                    skipLevel = h.level;
                    current = null;
                    return;
                }
                String low = h.title.toLowerCase(Locale.ROOT);
                inStatements = low.contains("oświadcz");
                inAttachments = low.startsWith("załącznik") || low.contains("załączniki") || low.contains("do wniosku należy dołączyć")
                        || low.contains("dokumenty dołączone");
                attachmentsOptional = low.matches("(?U).*(postaw|zaznacz).*");
                optionsWithoutGlyphs = !leaderDocument && (low.contains("postaw") || low.contains("zaznacz") || low.contains("wybierz"));
                conditionalOn = null;
                startSection(cleanLabel(h.title), h.level);
                if (optionsWithoutGlyphs) {
                    openOptionGroup(cleanLabel(h.title), true);
                }
                return;
            }
            if (skipping) {
                return;
            }

            if (line.toLowerCase(Locale.ROOT).startsWith("oświadczam")) {
                closeOptionGroup();
                body.statements.add(new FieldSpec.Consent(html(line), true));
                return;
            }
            if (inStatements) {
                Matcher item = ITEM.matcher(line);
                String stmt = item.matches() ? item.group(2) : line;
                if (!TextLines.hasLeader(stmt) && stmt.length() > 10) {
                    String s = stmt.strip();
                    s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
                    s = s.replaceAll("[,;]$", ".");
                    String text = s.toLowerCase(Locale.ROOT).startsWith("oświadczam") ? s
                            : "Oświadczam, że " + Character.toLowerCase(s.charAt(0)) + s.substring(1);
                    body.statements.add(new FieldSpec.Consent(html(text), true));
                    return;
                }
            }

            Matcher alt = ALTERNATIVE.matcher(line);
            boolean addedAlt = false;
            while (alt.find()) {
                List<String> opts = new ArrayList<>();
                for (String o : alt.group(1).split("/")) {
                    opts.add(Character.toUpperCase(o.charAt(0)) + o.substring(1));
                }
                if (opts.size() >= 2) {
                    ensureSection();
                    FieldSpec radio = FieldSpec.of(Type.RADIO, "Wybierz: " + String.join(" lub ", opts.stream()
                            .map(o -> o.toLowerCase(Locale.ROOT)).toList()), names.unique(String.join("_", opts))).options(opts).required();
                    radio.description = html(stripStars(line));
                    addField(radio);
                    addedAlt = true;
                }
            }

            if (TextLines.CHECKBOX.matcher(line).find()) {
                String opt = TextLines.withoutCheckbox(line);
                if (opt.isEmpty()) {
                    return;
                }
                if (inAttachments) {
                    addAttachment(opt);
                    return;
                }
                if (optionGroup == null) {
                    openOptionGroup(pendingLabel != null ? pendingLabel : (current != null ? current.title : "Wybierz"), false);
                    if (carriedOption != null) {
                        addOption(carriedOption);
                        carriedOption = null;
                    }
                }
                addOption(opt);
                lastOptionLine = i;
                return;
            }

            if (inAttachments && !TextLines.hasLeader(line) && line.length() > 8) {
                Matcher item = ITEM.matcher(line);
                String a = item.matches() ? item.group(2) : line.replaceFirst("^[-–•]\\s*", "");
                if (!a.toLowerCase(Locale.ROOT).startsWith("do wniosku")) {
                    addAttachment(a);
                }
                return;
            }

            if (optionsWithoutGlyphs && optionGroup != null && !TextLines.hasLeader(line)) {
                if (line.toLowerCase(Locale.ROOT).startsWith("jeśli") || line.toLowerCase(Locale.ROOT).startsWith("jeżeli")) {
                    FieldSpec g = optionGroup;
                    String lastOpt = g.options.isEmpty() ? null : g.options.getLast();
                    closeOptionGroup();
                    if (lastOpt != null) {
                        conditionalOn = Condition.answerIs(current.techName, g.techName, lastOpt);
                    }
                    optionsWithoutGlyphs = false;
                    return;
                }
                if (line.split("\\s+").length <= 10) {
                    addOption(cleanLabel(line));
                    return;
                }
            }

            if (TextLines.isHint(line)) {
                String hint = TextLines.stripHint(line);
                if (lastField != null && lastField.isInput() && lastField.description.isEmpty()) {
                    if (lastField.title.isBlank() || lastField.title.equals("Uzupełnij")) {
                        lastField.title = capitalize(hint);
                    } else {
                        lastField.description = capitalize(hint);
                    }
                    if (FieldTyper.isOptional(hint)) {
                        lastField.validation.remove("REQUIRED");
                    }
                }
                return;
            }

            if (TextLines.hasLeader(line) && inAttachments) {
                Matcher item = ITEM.matcher(line);
                String rest = item.matches() ? item.group(2) : line;
                if (TextLines.isLeaderOnly(rest)) {
                    if (!otherAttachmentAdded) {
                        otherAttachmentAdded = true;
                        ensureSection();
                        addField(FieldSpec.of(Type.FILE_UPLOADER, "Inny załącznik", names.unique("inny_zalacznik"))
                                .description("Opcjonalnie: dodaj inne dokumenty, które chcesz dołączyć do wniosku."));
                    }
                    return;
                }
            }

            if (TextLines.hasLeader(line)) {
                closeOptionGroup();
                leaderLine(line, i);
                return;
            }

            if (addedAlt) {
                return;
            }

            boolean endsColon = line.endsWith(":");
            boolean nextLeaderOnly = TextLines.isLeaderOnly(next);
            int words = line.split("\\s+").length;
            if (endsColon && words <= 12 && !inAttachments && !inStatements && ITEM.matcher(next).matches()
                    && lines.subList(i + 2, Math.min(lines.size(), i + 6)).stream().anyMatch(l -> ITEM.matcher(l).matches())) {
                closeOptionGroup();
                openOptionGroup(cleanLabel(line), true);
                numberedOptions = true;
                return;
            }
            if ((endsColon || nextLeaderOnly) && words <= 25) {
                // „❑ suka / ❑ mikroprocesor …… / Sposób oznakowania: / ❑ tatuaż” – opcja należy do następnej grupy
                if (optionGroup != null && lastOptionLine == i - 1 && optionGroup.options.size() > 2
                        && TextLines.hasLeader(lines.get(i - 1)) && TextLines.CHECKBOX.matcher(next).find()) {
                    optionGroup.options.removeLast();
                    deferred.removeLast();
                    carriedOption = TextLines.withoutCheckbox(lines.get(i - 1));
                }
                closeOptionGroup();
                pendingLabel = cleanLabel(line);
                if (TextLines.CHECKBOX.matcher(next).find()) {
                    return;
                }
                if (!nextLeaderOnly && !leaderDocument) {
                    addTextField(pendingLabel, 1, line);
                    pendingLabel = null;
                }
                return;
            }
            boolean sentence = lowLine.matches("(?U)^(wnoszę|proszę|zwracam się|oświadczam|uprzejmie|w związku|zgodnie|na podstawie).*")
                    || (!next.isEmpty() && Character.isLowerCase(next.codePointAt(0)));
            if (!leaderDocument && !sentence && words <= 8 && !line.endsWith(".") && Character.isUpperCase(line.codePointAt(0))) {
                closeOptionGroup();
                if (!(current == null && APPLICANT_FIELD.matcher(cleanLabel(line)).matches())) {
                    addTextField(cleanLabel(line), 1, line);
                }
                return;
            }
            if (leaderDocument && !sentence && words <= 7 && !line.endsWith(".") && Character.isUpperCase(line.codePointAt(0))
                    && !TextLines.CHECKBOX.matcher(next).find()) {
                closeOptionGroup();
                startSection(cleanLabel(line), 3);
                return;
            }
            if (words <= 7 && TextLines.CHECKBOX.matcher(next).find()) {
                closeOptionGroup();
                pendingLabel = cleanLabel(line);
            }
        }

        private Heading heading(String line, String next) {
            if (TextLines.hasLeader(line) || line.length() > 160) {
                return null;
            }
            Matcher n = NUMBERED.matcher(line);
            if (n.matches()) {
                String text = n.group(2);
                if (Character.isLowerCase(text.codePointAt(0)) || text.toLowerCase(Locale.ROOT).startsWith("oświadczam")) {
                    return null;
                }
                if (text.endsWith(":") && TextLines.isLeaderOnly(next)) {
                    return null;
                }
                if (ITEM.matcher(line).matches() && (inStatements || inAttachments) && !text.matches("(?U)\\p{Lu}.*")) {
                    return null;
                }
                if (inStatements && !text.toLowerCase(Locale.ROOT).contains("załącz") && text.length() > 25) {
                    return null;
                }
                if (inAttachments && text.length() > 15 && !text.equals(text.toUpperCase(Locale.ROOT))) {
                    return null;
                }
                if (text.split("\\s+").length > 20) {
                    return null;
                }
                return new Heading(text, n.group(1).split("\\.").length, currentGroup);
            }
            Matcher l = LETTERED.matcher(line);
            if (l.matches()) {
                return new Heading(l.group(3), l.group(2) == null ? 1 : 2, currentGroup);
            }
            if (line.matches("^[A-H]$") && !next.isEmpty() && next.equals(next.toUpperCase(Locale.ROOT))) {
                return null;
            }
            Matcher s = SUBLETTER.matcher(line);
            if (s.matches()) {
                return new Heading(s.group(2), 3, currentGroup);
            }
            String head = line.contains("(") ? line.substring(0, line.indexOf('(')) : line;
            String letters = head.replaceAll("[^\\p{L}]", "");
            if (letters.length() >= 6 && letters.equals(letters.toUpperCase(Locale.ROOT)) && head.strip().split("\\s+").length >= 2) {
                return new Heading(line, 1, currentGroup);
            }
            return null;
        }

        private void leaderLine(String line, int idx) {
            List<String> labels = TextLines.segmentsBeforeLeaders(line);
            String tail = TextLines.tailAfterLastLeader(line);
            int leaderLines = 1;
            for (int j = idx + 1; j < lines.size() && TextLines.isLeaderOnly(lines.get(j)); j++) {
                leaderLines++;
            }
            if (TextLines.isLeaderOnly(line)) {
                String after = idx + 1 < lines.size() ? lines.get(idx + 1).toLowerCase(Locale.ROOT) : "";
                if (after.contains("podpis") || after.matches("(?U)^[(/]\\s*(data|miejscowość)\\b.*")
                        || after.matches("(?U)^data( i podpis.*)?\\s*$")) {
                    return;
                }
                if (lastField != null && lastField.isInput() && pendingLabel == null && idx > 0
                        && (TextLines.hasLeader(lines.get(idx - 1)) || TextLines.isLeaderOnly(lines.get(idx - 1)))) {
                    return;
                }
                String label = pendingLabel != null ? pendingLabel : "Uzupełnij";
                pendingLabel = null;
                if (current == null && APPLICANT_FIELD.matcher(label).matches()) {
                    return;
                }
                addTextField(label, leaderLines, label);
                return;
            }
            List<String> fieldLabels = new ArrayList<>();
            for (int k = 0; k < labels.size(); k++) {
                String raw = labels.get(k);
                if (k == 0 && raw.isBlank() && pendingLabel != null) {
                    raw = pendingLabel;
                }
                if (k == 0 && raw.isBlank() && labels.size() > 1 && idx > 0 && TextLines.hasLeader(lines.get(idx - 1))) {
                    continue;
                }
                fieldLabels.add(raw);
            }
            pendingLabel = null;
            if (fieldLabels.size() >= 2 && current != null && !current.fields.isEmpty()) {
                List<String> slugs = fieldLabels.stream().map(this::phraseLabel).toList();
                int n = slugs.size();
                if (current.fields.size() >= n) {
                    boolean same = true;
                    for (int k = 0; k < n; k++) {
                        FieldSpec f = current.fields.get(current.fields.size() - n + k);
                        if (!f.title.equalsIgnoreCase(slugs.get(k))) {
                            same = false;
                            break;
                        }
                    }
                    if (same) {
                        markRepeating();
                        return;
                    }
                }
            }
            if (current != null && current.repeating) {
                startSection("Pozostałe informacje", 3);
            }
            for (int k = 0; k < fieldLabels.size(); k++) {
                String label = phraseLabel(fieldLabels.get(k));
                String unit = k == fieldLabels.size() - 1 ? tail : "";
                Matcher countUnit = COUNT_UNIT.matcher(unit);
                if (label.replaceAll("[^\\p{L}]", "").length() <= 3 && countUnit.lookingAt()) {
                    label = "Liczba " + countUnit.group(1).toLowerCase(Locale.ROOT);
                    unit = countUnit.group(1);
                }
                if (current == null && APPLICANT_FIELD.matcher(label).matches()) {
                    continue;
                }
                FieldSpec f = addTextField(label, k == fieldLabels.size() - 1 ? leaderLines : 1, fieldLabels.get(k) + " " + unit);
                f.sourceText = truncate(stripStars(line), 300);
                if (!unit.isBlank() && unit.length() <= 6) {
                    if (f.type == Type.TEXT_FIELD && unit.matches("(?iU)m2|m²|zł|ha|dni|szt\\.?|%|km|m")) {
                        replaceType(f);
                    }
                    f.description = "Jednostka: " + unit;
                }
            }
        }

        private String phraseLabel(String raw) {
            String s = cleanLabel(raw.replaceAll("\\(Dz\\.\\s?U\\.[^)]*\\)", " "));
            s = s.replaceFirst("(?iU)^.*?\\b(proszę o|wnoszę o|uprzejmie proszę o|zwracam się z prośbą o)\\s+", "");
            s = s.replaceFirst("(?iU)^(oraz|a|i|tj\\.?|w tym)\\s+", "");
            String low = s.toLowerCase(Locale.ROOT);
            if (low.endsWith("w terminie od") || low.endsWith("od dnia") || low.equals("od")) {
                return "Data rozpoczęcia";
            }
            if (low.equals("do") || low.equals("do dnia") || low.endsWith(" do dnia")) {
                return "Data zakończenia";
            }
            if (low.endsWith("w miejscowości") || low.equals("miejscowość")) {
                return "Miejscowość";
            }
            if (low.endsWith("przy ulicy")) {
                return "Ulica";
            }
            if (low.endsWith("w obrębie")) {
                return "Obręb";
            }
            if (low.endsWith("zam.")) {
                return "Adres zamieszkania";
            }
            if (low.equals("tel.") || low.equals("tel")) {
                return "Numer telefonu";
            }
            s = s.replaceAll("(?iU)\\s+(w|we|o|od|do|na|przy|z|ze|dla|pod|nr|numer|będzie)$", "");
            String[] w = s.split("\\s+");
            if (w.length > 8) {
                s = String.join(" ", java.util.Arrays.copyOfRange(w, w.length - 6, w.length));
            }
            s = s.replaceAll("(?iU)\\s+(w|we|o|od|do|na|przy|z|ze|dla|pod)$", "");
            return s.isBlank() ? "Uzupełnij" : capitalize(s);
        }

        private FieldSpec addTextField(String label, int leaderLines, String context) {
            ensureSection();
            String safeContext = context == null ? "" : context;
            String clean = capitalize(label.isBlank() ? "Uzupełnij" : label);
            Type type = FieldTyper.typeFor(clean, leaderLines);
            if (inAttachments || (FieldTyper.looksLikeAttachment(clean) && clean.toLowerCase(Locale.ROOT).startsWith("załącz"))) {
                type = Type.FILE_UPLOADER;
            }
            FieldSpec f = FieldSpec.of(type, truncate(clean, 200), names.unique(truncate(clean, 50)));
            f.sourceText = truncate(safeContext.strip(), 300);
            if (!FieldTyper.isOptional(safeContext)) {
                f.required();
            } else if (safeContext.toLowerCase(Locale.ROOT).contains("opcjonaln")) {
                f.title = f.title.replaceAll("(?iU)\\s*\\(opcjonalnie\\)", "");
            }
            FieldTyper.addValidators(f);
            if (conditionalOn != null) {
                f.when(conditionalOn);
            }
            addField(f);
            return f;
        }

        private void replaceType(FieldSpec f) {
            FieldSpec n = FieldSpec.of(Type.NUMBER, f.title, f.techName);
            n.validation.addAll(f.validation);
            n.description = f.description;
            n.showWhen = f.showWhen;
            n.sourceText = f.sourceText;
            int idx = current.fields.indexOf(f);
            if (idx >= 0) {
                current.fields.set(idx, n);
                lastField = n;
            }
        }

        private void addAttachment(String text) {
            if (text.strip().startsWith("*")) {
                return;
            }
            ensureSection();
            String t = cleanLabel(text);
            if (t.length() < 4) {
                return;
            }
            if (SIGNATURE.matcher(t).matches() || t.toLowerCase(Locale.ROOT).contains("podpis")) {
                return;
            }
            String title = truncate(t, 120);
            FieldSpec f = FieldSpec.of(Type.FILE_UPLOADER, title, names.unique(truncate(t, 50)));
            if (t.length() > 120) {
                f.description = t;
            }
            if (FieldTyper.isOptional(t)) {
                f.description = t;
            } else if (!attachmentsOptional) {
                f.required();
            }
            addField(f);
        }

        private void openOptionGroup(String label, boolean radioHint) {
            ensureSection();
            String clean = capitalize(splitInstruction(cleanLabel(label))[0]);
            optionGroup = FieldSpec.of(radioHint ? Type.RADIO : Type.CHECKBOX, truncate(clean, 200),
                    names.unique(truncate(clean, 50)));
            optionGroup.required();
            if (conditionalOn != null) {
                optionGroup.when(conditionalOn);
            }
            pendingLabel = null;
        }

        private void addOption(String raw) {
            String opt = raw;
            boolean details = TextLines.hasLeader(raw);
            if (details) {
                opt = TextLines.segmentsBeforeLeaders(raw).getFirst();
            }
            opt = capitalize(cleanLabel(opt.replaceFirst("^\\d+[.)]\\s*", "")));
            if (opt.isBlank()) {
                return;
            }
            optionGroup.options.add(truncate(opt, 200));
            if (details || opt.toLowerCase(Locale.ROOT).startsWith("inne") || opt.toLowerCase(Locale.ROOT).startsWith("inny")) {
                final String option = opt;
                FieldSpec group = optionGroup;
                String sectionTech = current.techName;
                deferred.add(() -> FieldSpec.of(Type.TEXT_FIELD, option + " – podaj szczegóły", names.unique(option + "_szczegoly"))
                        .required().when(Condition.answerIs(sectionTech, group.techName, option)));
            }
        }

        private static final Pattern COUNT_UNIT = Pattern.compile("(?iU)^(dni|dnia|miesięcy|tygodni|lat|szt)\\b");
        private final List<java.util.function.Supplier<FieldSpec>> deferred = new ArrayList<>();
        private String carriedOption;
        private int lastOptionLine = -2;

        private void closeOptionGroup() {
            if (optionGroup == null) {
                return;
            }
            FieldSpec g = optionGroup;
            optionGroup = null;
            if (g.options.isEmpty()) {
                return;
            }
            String low = g.title.toLowerCase(Locale.ROOT);
            boolean radio = g.type == Type.RADIO || g.options.size() == 2
                    || low.matches("(?U).*\\b(płeć|sposób|forma|rodzaj|czy|wybierz|tryb|status)\\b.*");
            if (low.contains("dokument") || low.contains("załącz") || low.contains("zaświadczeni") || g.options.size() > 6) {
                radio = g.type == Type.RADIO;
            }
            FieldSpec target = radio && g.type != Type.RADIO ? convert(g, Type.RADIO) : g;
            if (target.options.size() == 1 && target.type == Type.CHECKBOX) {
                target.validation.remove("REQUIRED");
            }
            if (target.options.size() > 5 && target.type == Type.RADIO) {
                FieldSpec sel = convert(target, Type.SELECT);
                sel.searchEnabled = target.options.size() > 15;
                target = sel;
            }
            if (target.options.size() > 8) {
                body.proposedDictionaries.add(target.title + ": " + String.join(", ", target.options));
            }
            addField(target);
            for (var d : deferred) {
                addField(d.get());
            }
            deferred.clear();
        }

        private FieldSpec convert(FieldSpec g, Type t) {
            FieldSpec n = FieldSpec.of(t, g.title, g.techName);
            n.options.addAll(g.options);
            n.validation.addAll(g.validation);
            n.showWhen = g.showWhen;
            n.description = g.description;
            return n;
        }

        private void markRepeating() {
            if (current.repeating) {
                return;
            }
            SectionSpec rep = SectionSpec.repeating(current.title, current.techName);
            rep.fields.addAll(current.fields);
            rep.showWhen = current.showWhen;
            rep.description = current.description;
            rep.minItemRows = 1;
            int idx = body.sections.indexOf(current);
            body.sections.set(idx, rep);
            body.levels.put(rep, body.levels.remove(current));
            body.groups.put(rep, body.groups.remove(current));
            current = rep;
        }

        private void ensureSection() {
            if (current == null) {
                startSection("Szczegóły wniosku", 1);
            }
        }

        private void startSection(String title, int level) {
            String[] split = splitInstruction(title);
            if (split[0].length() < 15 && !split[1].isEmpty() && split[1].length() <= 60
                    && !split[1].toLowerCase(Locale.ROOT).matches("(?U).*(postaw|zaznacz|wypełnij|opisz|wpisz|należy).*")) {
                split = new String[]{title.strip(), ""};
            }
            String base = split[0];
            if (base.equals(base.toUpperCase(Locale.ROOT)) && base.length() > 3) {
                base = base.toLowerCase(Locale.ROOT);
            }
            String t = truncate(capitalize(base.isBlank() ? "Szczegóły wniosku" : base), 120);
            SectionSpec s = SectionSpec.of(t, names.unique(truncate(t, 50)));
            if (!split[1].isEmpty()) {
                s.description = capitalize(split[1]);
            }
            body.sections.add(s);
            body.levels.put(s, level);
            body.groups.put(s, currentGroup);
            current = s;
            lastField = null;
        }

        private void addField(FieldSpec f) {
            ensureSection();
            current.fields.add(f);
            lastField = f;
        }

        private void finishSections() {
            List<SectionSpec> keep = new ArrayList<>();
            for (SectionSpec s : body.sections) {
                if (s.fields.isEmpty()) {
                    String low = s.title.toLowerCase(Locale.ROOT);
                    boolean meaningful = s.title.split("\\s+").length >= 2 && !low.startsWith("dane ") && !low.startsWith("adres");
                    if (meaningful && (low.matches("(?U).*\\b(uzasadnienie|opis|uwagi|cel|informacj\\w*|wyjaśnieni\\w*)\\b.*"))) {
                        s.fields.add(FieldSpec.of(Type.TEXTAREA, s.title, names.unique(s.title + "_tresc")).required());
                    } else {
                        continue;
                    }
                }
                keep.add(s);
            }
            body.sections.retainAll(keep);
        }
    }


    private List<PageSpec> paginate(Body body) {
        List<PageSpec> pages = new ArrayList<>();
        if (body.sections.isEmpty()) {
            PageSpec p = PageSpec.main("Szczegóły wniosku");
            p.add(SectionSpec.of("Treść wniosku", names.unique("Treść wniosku"))
                    .add(FieldSpec.of(Type.TEXTAREA, "Treść wniosku", names.unique("tresc_wniosku")).required()));
            pages.add(p);
            return pages;
        }
        String groupField = null;
        String groupSection = null;
        if (!body.groupNames.isEmpty()) {
            groupSection = names.unique("rodzaj_wniosku_sekcja");
            groupField = names.unique("rodzaj_wniosku");
            PageSpec first = PageSpec.main("Rodzaj wniosku");
            first.add(SectionSpec.of("Rodzaj wniosku", groupSection)
                    .add(FieldSpec.of(Type.RADIO, "Wybierz, o co wnioskujesz", groupField).options(body.groupNames).required()));
            pages.add(first);
        }
        PageSpec page = null;
        String pageGroup = null;
        java.util.Set<String> usedTitles = new java.util.HashSet<>(List.of("Dane", "Kontakt i zgody", "Strona końcowa", "Rodzaj wniosku"));
        for (SectionSpec s : body.sections) {
            int level = body.levels.getOrDefault(s, 1);
            String group = body.groups.get(s);
            int count = page == null ? 0 : page.fieldCount();
            boolean newGroup = group != null && !group.equals(pageGroup);
            if (page == null || newGroup || count + s.fields.size() > 16 || (level <= 1 && count >= 8)) {
                String pTitle = uniqueTitle(group != null && newGroup ? shortGroup(group) : s.title, usedTitles);
                page = PageSpec.main(pTitle);
                pages.add(page);
                pageGroup = group;
            }
            if (group != null && groupField != null) {
                Condition c = Condition.answerIs(groupSection, groupField, group);
                s.showWhen = s.showWhen == null ? c : Condition.and(c, s.showWhen);
            }
            page.add(s);
        }
        if (!body.feeLines.isEmpty() && page != null) {
            StringBuilder html = new StringBuilder("<html><head></head><body>");
            for (String l : body.feeLines) {
                html.append("<p>").append(l.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("</p>");
            }
            html.append("</body></html>");
            page.add(SectionSpec.of("Opłata", names.unique("oplata_informacja"))
                    .add(FieldSpec.of(Type.TEXT_BLOCK, "", names.unique("oplata_informacja_tekst")).description(html.toString())));
        }
        return pages;
    }

    private static String shortGroup(String group) {
        return truncate(group.replaceFirst("(?iU)^wniosek o przyznanie ", "").replaceFirst("(?iU)^wniosek o ", ""), 60);
    }

    private static String uniqueTitle(String t, java.util.Set<String> used) {
        String base = truncate(capitalize(t), 60);
        String c = base;
        int i = 2;
        while (!used.add(c)) {
            c = base + " (" + i++ + ")";
        }
        return c;
    }


    static String[] splitInstruction(String s) {
        java.util.regex.Matcher m = Pattern.compile("^(.*?\\S)\\s*[(\\[]([^()]{3,})[)\\]]\\s*[:.]?$").matcher(s.strip());
        if (m.matches() && m.group(1).length() >= 3) {
            return new String[]{m.group(1).strip(), m.group(2).strip()};
        }
        int open = s.indexOf(" (");
        if (open >= 3 && s.indexOf(')', open) < 0) {
            return new String[]{s.substring(0, open).strip(), s.substring(open + 2).strip()};
        }
        return new String[]{s.strip(), ""};
    }

    static String cleanLabel(String s) {
        String r = s.replaceAll("\\*+", "")
                .replaceAll("^\\s*(\\d{1,2}(\\.\\d{1,2})*[.)]|[a-h][/)]|[A-H]\\.(\\d+)?|[-–•])\\s*", "")
                .replaceAll("\\s+\\d\\)$", "")
                .replaceAll("(?<=\\p{L})\\d\\)$", "")
                .replaceAll("^(?i)nr\\.?\\s+", "Numer ")
                .replaceAll("(?i)\\bnr\\.?\\s+(?=\\p{L})", "numer ")
                .replaceAll("[:;,.\\s]+$", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
        if (r.equals(r.toUpperCase(Locale.ROOT)) && r.length() > 3) {
            r = r.toLowerCase(Locale.ROOT);
        }
        return r;
    }

    private static String stripStars(String s) {
        return s.replaceAll("\\*+", "").replaceAll(TextLines.LEADER.pattern(), " … ").replaceAll("\\s+", " ").strip();
    }

    static String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s == null ? "" : s;
        }
        String t = s.strip();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        String cut = s.substring(0, max);
        int sp = cut.lastIndexOf(' ');
        return (sp > max / 2 ? cut.substring(0, sp) : cut).strip();
    }

    private static String html(String text) {
        return "<html><head></head><body><p>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</p></body></html>";
    }
}
