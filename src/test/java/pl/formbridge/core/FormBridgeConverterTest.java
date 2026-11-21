package pl.formbridge.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormBridgeConverterTest {

    private final FormBridgeConverter converter = new FormBridgeConverter();

    @Test
    void everyChallengeFormProducesValidConfiguration() throws Exception {
        for (String name : Fixtures.ALL) {
            JsonNode form = converter.convertToNode(Fixtures.document(name));
            assertTrue(FormRules.check(form).isEmpty(), name + ": " + FormRules.check(form));
            JsonNode tabs = form.path("tabs");
            assertEquals("Dane", tabs.get(0).path("title").asText(), name);
            assertEquals("FINISH", tabs.get(tabs.size() - 1).path("type").asText(), name);
            assertTrue(tabs.size() >= 4, name + " ma za mało stron");
        }
    }

    @Test
    void outputIsDeterministic() throws Exception {
        ExtractedDocument doc = Fixtures.document("wniosek-swietlica");
        assertEquals(converter.convert(doc), converter.convert(doc));
    }

    @Test
    void standardSectionsFollowReferenceTechNames() throws Exception {
        JsonNode form = converter.convertToNode(Fixtures.document("wniosek-numer-porzadkowy"));
        List<String> sections = Fixtures.fields(form).stream()
                .filter(f -> f.path("type").asText().endsWith("SECTION") && !f.path("type").asText().equals("CONSENT_SECTION"))
                .map(f -> f.path("techName").asText()).toList();
        for (String expected : List.of("podmiot_wniosku", "twoje_dane", "adres_zamieszkania", "dane_podmiotu",
                "adres_siedziby", "dane_innej_osoby", "pelnomocnictwo", "oplata_skarbowa_pelnomocnictwo_1",
                "dane_do_kontaktu", "podglad_uzupelnionego_wniosku")) {
            assertTrue(sections.contains(expected), "brak sekcji " + expected);
        }
        assertEquals("Wójt Gminy Elbląg", Fixtures.field(form, "nazwa_organu").path("defaultValue").asText());
        assertEquals("MLPZ.name", Fixtures.field(form, "imie").path("defaultValue").asText());
        assertEquals("EXTERNAL_SYSTEM", Fixtures.field(form, "imie").path("defaultValueType").asText());
        assertTrue(Fixtures.field(form, "numer_pesel").path("validation").toString().contains("PESEL"));
    }

    @Test
    void conditionsUseReferenceJsonataShape() throws Exception {
        JsonNode form = converter.convertToNode(Fixtures.document("wniosek-numer-porzadkowy"));
        String query = Fixtures.field(form, "dane_innej_osoby").path("states").get(0).path("query").asText();
        assertEquals("(\"Innej osoby\" in tabs['Dane' in title].questions['podmiot_wniosku' in field.techName]"
                + ".field.repeatingSectionGrid['wybierz_w_czyim_imieniu_wnioskujesz' in field.techName].field.__userAnswer)", query);
    }

    @Test
    void emptyDocumentIsRejected() {
        ExtractedDocument empty = new ExtractedDocument("pusty.pdf", "application/pdf", List.of("  "), false);
        assertThrows(FormConversionException.class, () -> converter.convert(empty));
    }

    @Test
    void layoutMatchesReferenceGrid() throws Exception {
        JsonNode form = converter.convertToNode(Fixtures.document("wniosek-zaswiadczenie"));
        for (JsonNode tab : form.path("tabs")) {
            int y = 0;
            for (JsonNode q : tab.path("questions")) {
                assertEquals(0, q.path("x").asInt());
                assertEquals(3, q.path("cols").asInt());
                assertEquals(y, q.path("y").asInt());
                assertEquals(q.path("field").path("repeatingSectionGrid").size() + 1, q.path("rows").asInt());
                y += q.path("rows").asInt();
            }
        }
        assertFalse(form.path("flow").path("links").isEmpty());
    }
}
