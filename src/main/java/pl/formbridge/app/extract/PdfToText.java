package pl.formbridge.app.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

class PdfToText {

    private static final Logger LOG = Logger.getLogger(PdfToText.class.getName());

    Optional<List<String>> pages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            for (int p = 1; p <= document.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(document);
                if (!text.isBlank()) {
                    pages.add(text);
                }
            }
            return pages.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(pages));
        } catch (IOException e) {
            LOG.log(Level.FINE, "PDFBox nie odczytał tekstu, używam Tiki", e);
            return Optional.empty();
        }
    }
}
