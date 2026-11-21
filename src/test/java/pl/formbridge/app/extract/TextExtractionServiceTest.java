package pl.formbridge.app.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.formbridge.app.Samples;
import pl.formbridge.app.web.ApiException;
import pl.formbridge.core.ExtractedDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    private static ExtractedDocument extract(TextExtractionService service, String name, String mime, byte[] content) {
        return service.extract(name, mime, new ByteArrayInputStream(content));
    }

    private static TextExtractionService withOcr(OcrEngine ocr) {
        return new TextExtractionService(ocr);
    }

    @Test
    @DisplayName("PDF z warstwą tekstową dzielony jest na strony, bez OCR")
    void splitsPdfIntoPages() throws IOException {
        FakeOcr ocr = new FakeOcr("z ocr");
        byte[] pdf = Samples.pdfPages(new String[]{
                "Wniosek o wydanie zaswiadczenia\nImie i nazwisko wnioskodawcy oraz adres zamieszkania",
                "Druga strona wniosku z oswiadczeniami i podpisem wnioskodawcy",
                "Trzecia strona z pouczeniem o oplacie skarbowej w wysokosci 17 zl"});

        ExtractedDocument doc = extract(withOcr(ocr), "wniosek.pdf", "application/pdf", pdf);

        assertThat(doc.pages()).hasSize(3);
        assertThat(doc.pages().get(0)).contains("Wniosek o wydanie zaswiadczenia");
        assertThat(doc.pages().get(1)).contains("Druga strona");
        assertThat(doc.pages().get(2)).contains("Trzecia strona");
        assertThat(doc.ocrUsed()).isFalse();
        assertThat(ocr.calls()).isZero();
    }

    @Test
    @DisplayName("PDF bez warstwy tekstowej trafia do OCR, strona po stronie")
    void ocrsPdfWithoutTextLayer() throws IOException {
        FakeOcr ocr = new FakeOcr("Tekst ze skanu strony {n}");

        ExtractedDocument doc = extract(withOcr(ocr), "skan.pdf", "application/pdf", Samples.emptyPdf(2));

        assertThat(ocr.calls()).isEqualTo(2);
        assertThat(doc.pages()).containsExactly("Tekst ze skanu strony 1", "Tekst ze skanu strony 2");
        assertThat(doc.ocrUsed()).isTrue();
    }

    @Test
    @DisplayName("PDF renderowany do OCR ma 200 DPI")
    void rendersPdfAt200Dpi() throws IOException {
        FakeOcr ocr = new FakeOcr("skan");

        extract(withOcr(ocr), "skan.pdf", "application/pdf", Samples.emptyPdf(1));

        int[] size = ocr.sizes().get(0);
        assertThat(size[0]).isBetween(1600, 1700);
        assertThat(size[1]).isBetween(2300, 2400);
    }

    @Test
    @DisplayName("PDF ze zbyt krótkim tekstem na stronę uznawany jest za skan")
    void treatsShortTextAsScan() throws IOException {
        FakeOcr ocr = new FakeOcr("Pelny tekst wniosku rozpoznany przez OCR");

        ExtractedDocument doc = extract(withOcr(ocr), "skan.pdf", "application/pdf", Samples.pdf("Nr 1"));

        assertThat(ocr.calls()).isEqualTo(1);
        assertThat(doc.ocrUsed()).isTrue();
        assertThat(doc.pages()).containsExactly("Pelny tekst wniosku rozpoznany przez OCR");
    }

    @Test
    @DisplayName("Gdy OCR jest niedostępny, zostaje tekst z warstwy PDF")
    void fallsBackToPdfTextWhenOcrUnavailable() throws IOException {
        FakeOcr ocr = new FakeOcr("nieużywane", false);

        ExtractedDocument doc = extract(withOcr(ocr), "skan.pdf", "application/pdf", Samples.pdf("Nr 1"));

        assertThat(ocr.calls()).isZero();
        assertThat(doc.ocrUsed()).isFalse();
        assertThat(doc.pages()).hasSize(1);
    }

    @Test
    @DisplayName("PDF ze skanem w obrazie nie wywraca parsowania i trafia do OCR")
    void handlesPdfWithEmbeddedScan() throws IOException {
        FakeOcr ocr = new FakeOcr("Tresc skanu strony {n}");

        ExtractedDocument doc = extract(withOcr(ocr), "skan.pdf", "application/pdf", Samples.scannedPdf(2));

        assertThat(ocr.calls()).isEqualTo(2);
        assertThat(doc.pages()).containsExactly("Tresc skanu strony 1", "Tresc skanu strony 2");
        assertThat(doc.ocrUsed()).isTrue();
    }

    @Test
    @DisplayName("XLSX – jeden element pages na arkusz, komórki rozdzielone tabulatorem")
    void splitsXlsxBySheet() throws IOException {
        byte[] xlsx = Samples.xlsx(new String[]{"Dane wnioskodawcy", "Zalaczniki"});

        ExtractedDocument doc = extract(withOcr(new FakeOcr("")), "tabela.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        assertThat(doc.pages()).hasSize(2);
        assertThat(doc.pages().get(0)).contains("Imię i nazwisko\tPESEL");
        assertThat(doc.pages().get(0)).contains("Jan Kowalski\t44051401458");
        assertThat(doc.pages().get(1)).contains("Zalaczniki");
        assertThat(doc.ocrUsed()).isFalse();
    }

    @Test
    @DisplayName("ODS – jeden element pages na arkusz")
    void splitsOdsBySheet() throws IOException {
        ExtractedDocument doc = extract(withOcr(new FakeOcr("")), "tabela.ods",
                "application/vnd.oasis.opendocument.spreadsheet", Samples.ods(3));

        assertThat(doc.pages()).hasSize(3);
        assertThat(doc.pages().get(0)).contains("Imię i nazwisko\tPESEL");
        assertThat(doc.pages().get(0)).contains("Jan Kowalski 1");
        assertThat(doc.pages().get(2)).contains("Jan Kowalski 3");
    }

    @Test
    @DisplayName("Arkusze nie zawierają pustych wierszy")
    void skipsEmptyRows() throws IOException {
        ExtractedDocument doc = extract(withOcr(new FakeOcr("")), "tabela.ods",
                "application/vnd.oasis.opendocument.spreadsheet", Samples.ods(1));

        assertThat(doc.pages().get(0).lines()).noneMatch(String::isBlank);
    }

    @Test
    @DisplayName("DOCX trafia do pages jako jedna strona")
    void keepsDocxAsSinglePage() throws IOException {
        ExtractedDocument doc = extract(withOcr(new FakeOcr("")), "wniosek.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Samples.formDocx());

        assertThat(doc.pages()).hasSize(1);
        assertThat(doc.pages().get(0)).contains("WNIOSEK O WYDANIE ZAŚWIADCZENIA");
        assertThat(doc.ocrUsed()).isFalse();
    }

    @Test
    @DisplayName("Zwykły obraz to jedna strona po OCR")
    void ocrsRegularImage() throws IOException {
        FakeOcr ocr = new FakeOcr("Tekst ze zdjecia");

        ExtractedDocument doc = extract(withOcr(ocr), "skan.png", "image/png", Samples.image("png", 1000, 1200));

        assertThat(ocr.calls()).isEqualTo(1);
        assertThat(doc.pages()).containsExactly("Tekst ze zdjecia");
        assertThat(doc.ocrUsed()).isTrue();
    }

    @Test
    @DisplayName("Wysoki obraz jest cięty na strony A4 i OCR-owany kawałkami")
    void slicesTallImage() throws IOException {
        FakeOcr ocr = new FakeOcr("Strona {n}");
        byte[] tall = Samples.image("png", 1000, 5000);

        ExtractedDocument doc = extract(withOcr(ocr), "sklejka.png", "image/png", tall);

        assertThat(ocr.calls()).isEqualTo(4);
        assertThat(doc.pages()).containsExactly("Strona 1", "Strona 2", "Strona 3", "Strona 4");
        assertThat(ocr.sizes().get(0)).containsExactly(1000, 1414);
        assertThat(ocr.sizes().get(3)).containsExactly(1000, 758);
        assertThat(doc.ocrUsed()).isTrue();
    }

    @Test
    @DisplayName("422 – obraz bez możliwości OCR")
    void failsWhenOcrUnavailableForImage() throws IOException {
        TextExtractionService service = withOcr(new FakeOcr("", false));
        byte[] png = Samples.png();

        assertThatThrownBy(() -> extract(service, "skan.png", "image/png", png))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("OCR");
    }

    @Test
    @DisplayName("422 – plik bez tekstu i bez wyniku OCR")
    void failsWhenNothingExtracted() throws IOException {
        TextExtractionService service = withOcr(new FakeOcr("   "));
        byte[] pdf = Samples.emptyPdf();

        assertThatThrownBy(() -> extract(service, "pusty.pdf", "application/pdf", pdf))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nie zawiera tekstu");
    }

    @Test
    @DisplayName("422 – uszkodzona treść pliku")
    void failsOnBrokenFile() {
        TextExtractionService service = withOcr(new FakeOcr(""));
        byte[] broken = "%PDF-1.4 to nie jest poprawny dokument".getBytes();

        assertThatThrownBy(() -> extract(service, "uszkodzony.pdf", "application/pdf", broken))
                .isInstanceOf(ApiException.class);
    }
}
