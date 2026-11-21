package pl.formbridge.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FormRules {

    private static final Set<String> INPUT_TYPES = Set.of("TEXT_FIELD", "TEXTAREA", "NUMBER", "DATEPICKER", "SELECT",
            "RADIO", "CHECKBOX", "FILE_UPLOADER", "CONSENT_SECTION");
    private static final Set<String> DICTIONARY_TYPES = Set.of("SELECT", "RADIO", "CHECKBOX");

    private FormRules() {
    }

    public static List<String> check(JsonNode form) {
        List<String> errors = new ArrayList<>();
        String title = form.path("title").asText("");
        if (title.isBlank() || title.length() > 255) {
            errors.add("tytuł formularza musi mieć od 1 do 255 znaków");
        }
        JsonNode tabs = form.path("tabs");
        if (tabs.size() < 2) {
            errors.add("formularz musi mieć co najmniej dwie strony");
        }
        Set<String> ids = new HashSet<>();
        Set<String> tabTitles = new HashSet<>();
        int finishCount = 0;
        for (int t = 0; t < tabs.size(); t++) {
            JsonNode tab = tabs.get(t);
            String type = tab.path("type").asText();
            boolean finish = "FINISH".equals(type);
            if (finish) {
                finishCount++;
                if (t != tabs.size() - 1) {
                    errors.add("strona końcowa musi być ostatnia");
                }
            } else if (!"MAIN".equals(type)) {
                errors.add("strona '" + tab.path("title").asText() + "' ma niepoprawny typ " + type);
            }
            if (!tabTitles.add(tab.path("title").asText())) {
                errors.add("powtórzony tytuł strony: " + tab.path("title").asText());
            }
            unique(ids, tab.path("id").asText(), errors);
            JsonNode questions = tab.path("questions");
            if (questions.isEmpty()) {
                errors.add("strona '" + tab.path("title").asText() + "' nie ma żadnego pola");
            }
            Set<String> sectionNames = new HashSet<>();
            for (JsonNode q : questions) {
                JsonNode section = q.path("field");
                String st = section.path("type").asText();
                if (!"SECTION".equals(st) && !"REPEATING_SECTION".equals(st)) {
                    errors.add("pole '" + section.path("techName").asText() + "' nie jest zagnieżdżone w sekcji");
                    continue;
                }
                if (!sectionNames.add(section.path("techName").asText())) {
                    errors.add("powtórzona nazwa techniczna sekcji: " + section.path("techName").asText());
                }
                unique(ids, section.path("id").asText(), errors);
                for (JsonNode child : section.path("repeatingSectionGrid")) {
                    JsonNode f = child.path("field");
                    String ft = f.path("type").asText();
                    unique(ids, f.path("id").asText(), errors);
                    if ("SECTION".equals(ft) || "REPEATING_SECTION".equals(ft)) {
                        errors.add("sekcja '" + f.path("techName").asText() + "' zagnieżdżona w sekcji");
                    }
                    if (f.path("techName").asText().isBlank()) {
                        errors.add("pole bez nazwy technicznej w sekcji " + section.path("techName").asText());
                    }
                    if (finish && INPUT_TYPES.contains(ft)) {
                        errors.add("na stronie końcowej nie może być pola do wypełnienia: " + f.path("techName").asText());
                    }
                    if (f.hasNonNull("dictionaryName") && !DICTIONARY_TYPES.contains(ft)) {
                        errors.add("słownik możliwy tylko w SELECT, RADIO, CHECKBOX: " + f.path("techName").asText());
                    }
                    if (("RADIO".equals(ft) || "CHECKBOX".equals(ft) || "SELECT".equals(ft))
                            && f.path("options").isEmpty() && !f.hasNonNull("dictionaryName")) {
                        errors.add("pole wyboru bez opcji i słownika: " + f.path("techName").asText());
                    }
                }
            }
        }
        if (finishCount != 1) {
            errors.add("formularz musi mieć dokładnie jedną stronę końcową (FINISH)");
        }
        int links = form.path("flow").path("links").size();
        if (links < tabs.size() - 1) {
            errors.add("strony muszą być połączone przepływem");
        }
        return errors;
    }

    private static void unique(Set<String> ids, String id, List<String> errors) {
        if (id.isBlank() || !ids.add(id)) {
            errors.add("brak lub powtórzony identyfikator: " + id);
        }
    }
}
