package pl.formbridge.app.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrTest {

    private static final BufferedImage IMAGE = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);

    @Test
    @DisplayName("Brak binarki Tesseracta zgłaszany jest jako niedostępność, bez wyjątku")
    void reportsMissingBinary() {
        TesseractOcr ocr = new TesseractOcr("pol", Duration.ofSeconds(5), "tesseract-ktorego-nie-ma");

        assertThat(ocr.available()).isFalse();
    }

    @Test
    @DisplayName("Błąd uruchomienia OCR daje pusty tekst, nie wyjątek")
    void returnsEmptyTextOnFailure() {
        TesseractOcr ocr = new TesseractOcr("pol", Duration.ofSeconds(5), "tesseract-ktorego-nie-ma");

        assertThat(ocr.recognize(IMAGE)).isEmpty();
    }

    @Test
    @DisplayName("Po nieudanym OCR nie zostają katalogi tymczasowe")
    void cleansUpTempDirectories() throws Exception {
        TesseractOcr ocr = new TesseractOcr("pol", Duration.ofSeconds(5), "tesseract-ktorego-nie-ma");

        long before = countTempDirs();
        ocr.recognize(IMAGE);
        assertThat(countTempDirs()).isEqualTo(before);
    }

    private static long countTempDirs() throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> paths = Files.list(tmp)) {
            return paths.filter(p -> p.getFileName().toString().startsWith("formbridge-ocr-")).count();
        }
    }
}
