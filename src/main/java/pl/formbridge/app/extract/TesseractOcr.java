package pl.formbridge.app.extract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.formbridge.app.config.FormBridgeProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Component
class TesseractOcr implements OcrEngine {

    private static final Logger LOG = Logger.getLogger(TesseractOcr.class.getName());

    private final String language;
    private final Duration timeout;
    private final String binary;

    @Autowired
    TesseractOcr(FormBridgeProperties properties) {
        this(properties.ocr().language(), properties.ocr().timeout(), "tesseract");
    }

    TesseractOcr(String language, Duration timeout, String binary) {
        this.language = language;
        this.timeout = timeout;
        this.binary = binary;
    }

    @Override
    public boolean available() {
        try {
            Process p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String recognize(BufferedImage image) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("formbridge-ocr-");
            Path input = dir.resolve("page.png");
            ImageIO.write(image, "png", input.toFile());
            return run(input);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "OCR nie powiódł się", e);
            return "";
        } finally {
            deleteRecursively(dir);
        }
    }

    private String run(Path input) throws IOException {
        Process process = new ProcessBuilder(binary, input.toString(), "stdout", "-l", language, "--psm", "4")
                .redirectErrorStream(false)
                .start();
        try {
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                LOG.warning(() -> "OCR przekroczył limit " + timeout);
                return "";
            }
            if (process.exitValue() != 0) {
                LOG.warning(() -> "Tesseract zakończył się kodem " + process.exitValue());
                return "";
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> toDelete = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path p : toDelete) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Nie udało się usunąć katalogu tymczasowego " + dir, e);
        }
    }
}
