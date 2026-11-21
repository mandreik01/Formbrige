package pl.formbridge.app.extract;

import org.junit.jupiter.api.Test;
import pl.formbridge.app.Samples;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfToTextTest {

    private final PdfToText pdfToText = new PdfToText();

    @Test
    void readsPdfPageByPage() throws IOException {
        byte[] pdf = Samples.pdfPages(new String[]{"Pierwsza strona wniosku", "Druga strona wniosku"});

        List<String> pages = pdfToText.pages(pdf).orElseThrow();

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).contains("Pierwsza strona");
        assertThat(pages.get(1)).contains("Druga strona");
    }

    @Test
    void returnsEmptyForPdfWithoutText() throws IOException {
        assertThat(pdfToText.pages(Samples.emptyPdf(2))).isEmpty();
    }

    @Test
    void returnsEmptyForBrokenPdf() {
        assertThat(pdfToText.pages("to nie jest PDF".getBytes())).isEmpty();
    }
}
