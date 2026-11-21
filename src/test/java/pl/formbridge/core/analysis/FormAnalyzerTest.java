package pl.formbridge.core.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import pl.formbridge.core.Fixtures;
import pl.formbridge.core.FormBridgeConverter;
import pl.formbridge.core.TechNames;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormAnalyzerTest {

    private final FormBridgeConverter converter = new FormBridgeConverter();

    private JsonNode convert(String name) throws Exception {
        return converter.convertToNode(Fixtures.document(name));
    }

    @Test
    void numberOfPropertyFormHasLocationFields() throws Exception {
        List<String> texts = Fixtures.titles(convert("wniosek-numer-porzadkowy"), "TEXT_FIELD");
        assertTrue(texts.containsAll(List.of("Miejscowość", "Ulica", "Numer ewidencyjny działki", "Obręb")), texts.toString());
    }

    @Test
    void communityHallFormHasChoiceDatesAndNumber() throws Exception {
        JsonNode form = convert("wniosek-swietlica");
        assertTrue(Fixtures.titles(form, "RADIO").stream().anyMatch(t -> t.contains("wynajem lub użyczenie")));
        assertEquals(List.of("Data rozpoczęcia", "Data zakończenia"), Fixtures.titles(form, "DATEPICKER"));
        assertTrue(Fixtures.titles(form, "NUMBER").contains("Przewidywana ilość uczestników"));
        JsonNode consents = Fixtures.fields(form).stream().filter(f -> f.path("type").asText().equals("CONSENT_SECTION"))
                .findFirst().orElseThrow().path("consentSectionConfig").path("consents");
        assertTrue(consents.size() >= 5, "oświadczenia z formularza powinny trafić do zgód");
    }

    @Test
    void certificateFormHasCheckboxWithConditionalOther() throws Exception {
        JsonNode form = convert("wniosek-zaswiadczenie");
        JsonNode checkbox = Fixtures.fields(form).stream().filter(f -> f.path("type").asText().equals("CHECKBOX"))
                .findFirst().orElseThrow();
        assertEquals(5, checkbox.path("options").size());
        JsonNode other = Fixtures.fields(form).stream().filter(f -> f.path("title").asText().startsWith("Inne"))
                .filter(f -> f.path("type").asText().equals("TEXT_FIELD")).findFirst().orElseThrow();
        assertTrue(other.path("states").get(0).path("query").asText().contains("\"Inne\" in"));
    }

    @Test
    void feeRefundFormSkipsInstructionsAndAddsConditionalAccount() throws Exception {
        JsonNode form = convert("wniosek-zwrot-oplaty-skarbowej");
        List<String> all = Fixtures.fields(form).stream().map(f -> f.path("title").asText()).toList();
        assertFalse(all.stream().anyMatch(t -> t.toLowerCase().contains("wielkimi literami")), all.toString());
        JsonNode account = Fixtures.fields(form).stream().filter(f -> f.path("title").asText().equals("Numer rachunku"))
                .findFirst().orElseThrow();
        assertTrue(account.path("states").get(0).path("query").asText().contains("Przelewem"));
    }

    @Test
    void roadOccupationFormHasRepeatingSections() throws Exception {
        long repeating = Fixtures.fields(convert("wniosek-pas-drogowy")).stream()
                .filter(f -> f.path("type").asText().equals("REPEATING_SECTION")).count();
        assertTrue(repeating >= 3, "oczekiwano sekcji powtarzających się dla jezdni i chodnika");
    }

    @Test
    void combinedSportsFormLetsUserChooseApplicationType() throws Exception {
        JsonNode form = convert("wniosek-stypendium-sportowe");
        JsonNode choice = Fixtures.fields(form).stream().filter(f -> f.path("techName").asText().equals("rodzaj_wniosku"))
                .findFirst().orElseThrow();
        assertEquals(3, choice.path("options").size());
        long conditional = StreamSupport.stream(form.path("tabs").spliterator(), false)
                .flatMap(t -> StreamSupport.stream(t.path("questions").spliterator(), false))
                .filter(q -> q.path("field").path("states").toString().contains("rodzaj_wniosku")).count();
        assertTrue(conditional > 10, "sekcje poszczególnych wniosków powinny zależeć od wyboru");
    }

    @Test
    void titleUsesFileNameWhenTextTitleIsTruncated() {
        var analysis = new FormAnalyzer(new TechNames.Registry()).analyze(
                "Wniosek o ustalenie numeru porządkowego nieruchomości.pdf",
                "Wójt Gminy Elbląg\nWNIOSEK O USTALENIE NUMERU PORZĄDKOWEGO\nw miejscowości ........ przy ulicy ........\n");
        assertEquals("Wniosek o ustalenie numeru porządkowego nieruchomości", analysis.title());
        assertEquals("Wójt Gminy Elbląg", analysis.office());
        assertTrue(analysis.organ());
    }

    @Test
    void techNamesAreAsciiSnakeCase() {
        assertEquals("numer_ewidencyjny_dzialki", TechNames.slug("Numer ewidencyjny działki"));
        assertEquals("zrodlo_lacznosc", TechNames.slug("Źródło: łączność!"));
        TechNames.Registry r = new TechNames.Registry();
        assertEquals("imie", r.unique("Imię"));
        assertEquals("imie_2", r.unique("Imię"));
    }
}
