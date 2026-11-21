package pl.formbridge.app.extract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfToTextTest {

    @Test
    void returnsEmptyWhenBinaryIsMissing() {
        PdfToText missing = new PdfToText("formbridge-brak-pdftotext");

        assertThat(missing.pages(new byte[]{1, 2, 3})).isEmpty();
    }

    @Test
    void returnsEmptyForBrokenPdf() {
        assertThat(new PdfToText().pages("to nie jest PDF".getBytes())).isEmpty();
    }
}
