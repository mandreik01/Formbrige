package pl.formbridge.core.analysis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TextLines {

    static final Pattern LEADER = Pattern.compile("(?:[._][ \\t]?){4,}[ \\t]*");
    static final Pattern CHECKBOX = Pattern.compile("^[\\u25A1\\u25A0\\u25AB\\u25AA\\u25CB\\u25CF\\u25EF\\u2610\\u2611\\u2612"
            + "\\u274F\\u2750\\u2751\\u2752\\u274E\\uF000-\\uF0FF\\u2022\\u25E6\\u2023\\u2043\\u2219]\\s*");

    private static final Pattern DATE_MASK = Pattern.compile("(?:\\s+-){2,}\\s*$");
    private static final Pattern BOX = Pattern.compile(
            "[\\u25A1\\u2610\\u2611\\u2612\\u274F\\u2750\\u2751\\u2752\\u274E\\uF000-\\uF0FF]");
    private static final Pattern BOX_WITH_TEXT = Pattern.compile(BOX.pattern() + "\\s*(?=\\p{L})");

    private TextLines() {
    }

    private static final Pattern SLASH_HINT = Pattern.compile("^(/[^/]{1,30}/)\\s+(\\p{L}.*)$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d{1,2})\\.\\s");

    // tylko kolejne numery (8., 9., 10.), żeby nie pociąć zdania z liczbą w środku
    static List<String> splitNumbered(String line) {
        Matcher first = LEADING_NUMBER.matcher(line);
        if (!first.find()) {
            return List.of(line);
        }
        int n = Integer.parseInt(first.group(1));
        List<String> parts = new ArrayList<>();
        int from = 0;
        int searchFrom = first.end();
        int at;
        while ((at = line.indexOf(" " + (n + 1) + ". ", searchFrom)) >= 0) {
            parts.add(line.substring(from, at).strip());
            from = at + 1;
            searchFrom = at + 1;
            n++;
        }
        parts.add(line.substring(from).strip());
        return parts;
    }

    static List<String> splitInlineBoxes(String line) {
        List<Integer> starts = new ArrayList<>();
        int withText = 0;
        Matcher m = BOX_WITH_TEXT.matcher(line);
        while (m.find()) {
            withText++;
            if (m.start() > 0) {
                starts.add(m.start());
            }
        }
        // „TAK ❑ NIE ❑” (kratka bez opisu za nią) zostaje w całości
        if (starts.isEmpty() || withText != BOX.matcher(line).results().count()) {
            return List.of(line);
        }
        String head = line.substring(0, starts.get(0)).strip();
        if (!head.endsWith(":") && starts.size() < 2) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        parts.add(head);
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : line.length();
            parts.add(line.substring(starts.get(i), end).strip());
        }
        return parts;
    }

    static List<String> normalize(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\u00AD", "")
                .replace("\uFFFD", "")
                .replace('\u00A0', ' ')
                .replace("…", "....")
                .replace('\t', ' ')
                .replace('\f', '\n');
        List<String> out = new ArrayList<>();
        // PDFBox stawia kolumny strony obok siebie; 8+ spacji to granica kolumn
        for (String raw : t.split("\\R|[ ]{8,}")) {
            String line = DATE_MASK.matcher(raw.replaceAll("[ ]{2,}", " ")).replaceAll("").strip();
            Matcher hint = SLASH_HINT.matcher(line);
            if (hint.matches()) {
                out.add(hint.group(1));
                line = hint.group(2);
            }
            for (String numbered : splitNumbered(line)) {
                for (String part : splitInlineBoxes(numbered)) {
                    if (!part.isEmpty()) {
                        out.add(part);
                    }
                }
            }
        }
        return joinContinuations(out);
    }

    private static List<String> joinContinuations(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!out.isEmpty()) {
                String prev = out.get(out.size() - 1);
                boolean continuation = Character.isLowerCase(line.codePointAt(0))
                        && !hasLeader(prev) && !hasLeader(line)
                        && !CHECKBOX.matcher(line).find() && !CHECKBOX.matcher(prev).find()
                        && !prev.endsWith(":") && !prev.endsWith(".") && !isHint(line) && !line.endsWith(":")
                        && prev.length() > 40;
                if (continuation) {
                    out.set(out.size() - 1, prev + " " + line);
                    continue;
                }
            }
            out.add(line);
        }
        return out;
    }

    static boolean hasLeader(String line) {
        return LEADER.matcher(line).find();
    }

    static boolean isLeaderOnly(String line) {
        String rest = LEADER.matcher(line).replaceAll(" ").replaceAll("[\\s,;.\\-`']", "");
        return hasLeader(line) && rest.length() <= 3;
    }

    static boolean isHint(String line) {
        String l = line.strip();
        return (l.startsWith("(") && l.endsWith(")")) || (l.startsWith("/") && l.endsWith("/") && l.length() > 2);
    }

    static String stripHint(String line) {
        return line.strip().replaceAll("^[(/]\\s*", "").replaceAll("\\s*[)/]$", "").strip();
    }

    static List<String> segmentsBeforeLeaders(String line) {
        List<String> labels = new ArrayList<>();
        Matcher m = LEADER.matcher(line);
        int last = 0;
        while (m.find()) {
            labels.add(line.substring(last, m.start()).strip());
            last = m.end();
        }
        return labels;
    }

    static String tailAfterLastLeader(String line) {
        Matcher m = LEADER.matcher(line);
        int end = -1;
        while (m.find()) {
            end = m.end();
        }
        return end < 0 ? "" : line.substring(end).strip();
    }

    static String withoutCheckbox(String line) {
        return CHECKBOX.matcher(line).replaceFirst("").strip();
    }
}
