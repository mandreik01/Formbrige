package pl.formbridge.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.formbridge.core.model.FieldSpec;
import pl.formbridge.core.model.FieldSpec.Type;
import pl.formbridge.core.model.PageSpec;
import pl.formbridge.core.model.SectionSpec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class FormRefiner {

    public record Report(String model, int candidates, int refined, long millis, boolean timedOut) {
        public static Report skipped() {
            return new Report("", 0, 0, 0, false);
        }
    }

    private static final Set<Type> TEXT_TYPES = EnumSet.of(Type.TEXT_FIELD, Type.TEXTAREA, Type.NUMBER, Type.DATEPICKER);
    private static final Pattern WEAK = Pattern.compile("(?U).*(\\d\\)|\\(|\\)|/|:|\\bnr\\b|\\btj\\b|^[a-ząćęłńóśźż]).*");
    private static final String SYSTEM = """
            Jesteś projektantem formularzy eUsług. Zamieniasz robocze etykiety z papierowego formularza
            na krótkie, czytelne etykiety pól formularza elektronicznego (najwyżej 5 słów, forma rzeczownikowa).
            Usuń numerację, przypisy (np. „1)”), nawiasy, dwukropki, zwroty „proszę o”, „wnoszę o” i skróty („nr” → „numer”).
            Sekcji nadaj nazwę opisującą jej pola. Nie przepisuj etykiety bez zmian, jeśli da się ją poprawić.
            Typ pola: TEXT_FIELD, TEXTAREA (dłuższy opis), NUMBER (liczba, kwota, powierzchnia), DATEPICKER (data); dla sekcji "".
            Przykłady:
            POLE "Przy ulicy1)" -> "Ulica", TEXT_FIELD
            POLE "Wnoszę o stwierdzenie nadpłaty z tytułu" -> "Tytuł nadpłaty", TEXT_FIELD
            POLE "Przewidywana ilość uczestników" -> "Liczba uczestników", NUMBER
            SEKCJA "Szczegóły wniosku" z polami Miejscowość; Ulica; Numer działki -> "Lokalizacja nieruchomości"
            Odpowiadasz wyłącznie obiektem JSON.""";

    private final LlmClient client;
    private final Duration budget;
    private final int maxItems;
    private final int batchSize;
    private final ObjectMapper mapper = new ObjectMapper();

    public FormRefiner(LlmClient client, Duration budget, int maxItems, int batchSize) {
        this.client = client;
        this.budget = budget;
        this.maxItems = maxItems;
        this.batchSize = Math.max(1, batchSize);
    }

    public Report refine(String formTitle, List<PageSpec> pages, UnaryOperator<String> uniqueName) {
        long start = System.nanoTime();
        Map<String, Object> targets = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        int n = 0;
        for (PageSpec p : pages) {
            for (SectionSpec s : p.sections) {
                if (needsSectionName(s) && n < maxItems) {
                    String id = "s" + (++n);
                    targets.put(id, s);
                    lines.add(id + " | SEKCJA | " + q(s.title) + " | pola: " + q(fieldTitles(s)));
                }
                for (FieldSpec f : s.fields) {
                    if (n >= maxItems) {
                        break;
                    }
                    if (TEXT_TYPES.contains(f.type) && isWeak(f.title)) {
                        String id = "f" + (++n);
                        targets.put(id, f);
                        lines.add(id + " | POLE " + f.type + " | " + q(f.title) + " | kontekst: " + q(f.sourceText));
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return new Report(client.modelName(), 0, 0, 0, false);
        }
        int refined = 0;
        boolean timedOut = false;
        for (int i = 0; i < lines.size(); i += batchSize) {
            Duration left = budget.minusNanos(System.nanoTime() - start);
            if (left.isNegative() || left.isZero()) {
                timedOut = true;
                break;
            }
            List<String> batch = lines.subList(i, Math.min(lines.size(), i + batchSize));
            String user = "Formularz: " + formTitle + "\nElementy (id | rodzaj | etykieta | kontekst):\n"
                    + String.join("\n", batch)
                    + "\nZwróć {\"items\":[{\"id\":\"...\",\"label\":\"...\",\"type\":\"...\"}]} dla każdego elementu.";
            Optional<String> answer = client.complete(SYSTEM, user, schema(), left);
            if (answer.isEmpty()) {
                timedOut = Duration.ofNanos(System.nanoTime() - start).compareTo(budget) >= 0;
                if (timedOut) {
                    break;
                }
                continue;
            }
            refined += apply(answer.get(), targets, uniqueName);
        }
        return new Report(client.modelName(), targets.size(), refined, (System.nanoTime() - start) / 1_000_000, timedOut);
    }

    private int apply(String json, Map<String, Object> targets, UnaryOperator<String> uniqueName) {
        int count = 0;
        try {
            for (JsonNode item : mapper.readTree(json).path("items")) {
                Object target = targets.get(item.path("id").asText());
                String label = clean(item.path("label").asText(""));
                if (target == null || !acceptable(label)) {
                    continue;
                }
                if (target instanceof FieldSpec f) {
                    f.title = label;
                    f.techName = uniqueName.apply(label);
                    String t = item.path("type").asText("");
                    if (!t.isBlank()) {
                        try {
                            Type type = Type.valueOf(t);
                            if (TEXT_TYPES.contains(type) && type != f.type) {
                                retype(f, type);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // nieznany typ - zostaje typ z reguł
                        }
                    }
                    count++;
                } else if (target instanceof SectionSpec s) {
                    setSectionTitle(s, label);
                    count++;
                }
            }
        } catch (Exception e) {
            // zła odpowiedź modelu - zostają etykiety z reguł
        }
        return count;
    }

    private static void retype(FieldSpec f, Type type) {
        f.type = type;
    }

    private static void setSectionTitle(SectionSpec s, String title) {
        s.title = title;
    }

    private static boolean needsSectionName(SectionSpec s) {
        String t = s.title.toLowerCase(Locale.ROOT);
        return t.equals("szczegóły wniosku") || t.equals("pozostałe informacje") || s.title.length() > 50 || WEAK.matcher(s.title).matches();
    }

    static boolean isWeak(String label) {
        String l = label.strip();
        return l.equals("Uzupełnij") || l.length() <= 2 || l.length() > 45 || l.split("\\s+").length > 6
                || WEAK.matcher(l).matches()
                || l.matches("(?iU).*\\b(w|o|od|do|na|przy|z|dla)$");
    }

    private static boolean acceptable(String label) {
        return label.length() >= 2 && label.length() <= 80 && !label.contains("\n") && !label.contains("{");
    }

    private static String clean(String s) {
        String r = s.strip().replaceAll("[*:;]+$", "").replaceAll("\\s+", " ");
        return r.isEmpty() ? r : Character.toUpperCase(r.charAt(0)) + r.substring(1);
    }

    private static String fieldTitles(SectionSpec s) {
        List<String> t = new ArrayList<>();
        for (FieldSpec f : s.fields) {
            if (t.size() < 6) {
                t.add(f.title);
            }
        }
        return String.join("; ", t);
    }

    private static String q(String s) {
        String t = s == null ? "" : s.replace('\n', ' ').replace("|", "/");
        return "\"" + (t.length() > 220 ? t.substring(0, 220) : t) + "\"";
    }

    private ObjectNode schema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        ObjectNode items = props.putObject("items");
        items.put("type", "array");
        ObjectNode item = items.putObject("items");
        item.put("type", "object");
        ObjectNode ip = item.putObject("properties");
        ip.putObject("id").put("type", "string");
        ip.putObject("label").put("type", "string").put("minLength", 2).put("maxLength", 60);
        ArrayNode types = ip.putObject("type").put("type", "string").putArray("enum");
        types.add("TEXT_FIELD").add("TEXTAREA").add("NUMBER").add("DATEPICKER").add("");
        item.putArray("required").add("id").add("label").add("type");
        root.putArray("required").add("items");
        return root;
    }
}
