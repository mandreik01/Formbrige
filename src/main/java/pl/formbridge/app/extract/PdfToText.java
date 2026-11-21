package pl.formbridge.app.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

// Reguły analizy są strojone na wyjściu pdftotext (poppler), więc gdy jest dostępny,
// używamy go zamiast Tiki, która inaczej składa linie w formularzach z tabelami.
class PdfToText {

    private static final Logger LOG = Logger.getLogger(PdfToText.class.getName());
    private static final long TIMEOUT_SECONDS = 30;

    private final String command;

    PdfToText() {
        this("pdftotext");
    }

    PdfToText(String command) {
        this.command = command;
    }

    Optional<List<String>> pages(byte[] pdf) {
        Path in = null;
        try {
            in = Files.createTempFile("formbridge-", ".pdf");
            Files.write(in, pdf);
            Process process = new ProcessBuilder(command, "-enc", "UTF-8", in.toString(), "-")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            List<String> pages = Arrays.stream(new String(out, StandardCharsets.UTF_8).split("\f"))
                    .filter(p -> !p.isBlank())
                    .toList();
            return pages.isEmpty() ? Optional.empty() : Optional.of(pages);
        } catch (IOException e) {
            LOG.log(Level.FINE, "pdftotext niedostępny, używam Tiki", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (in != null) {
                try {
                    Files.deleteIfExists(in);
                } catch (IOException ignored) {
                    // plik tymczasowy w /tmp, system i tak go sprzątnie
                }
            }
        }
    }
}
