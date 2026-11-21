package pl.formbridge.app.upload;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import pl.formbridge.app.config.FormBridgeProperties;
import pl.formbridge.app.web.ApiException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class FileValidator {

    public static final int MAX_FILE_NAME_LENGTH = 250;

    public static final Set<Character> FORBIDDEN_CHARS =
            Set.of(';', ':', '>', '<', '/', '\\', '|', '\'', '*', '%', '$');

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String OOXML = "application/x-tika-ooxml";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword", "application/x-tika-msoffice", DOCX),
            "docx", Set.of(DOCX, OOXML),
            "ods", Set.of("application/vnd.oasis.opendocument.spreadsheet"),
            "xlsx", Set.of(XLSX, OOXML),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "jpe", Set.of("image/jpeg"),
            "png", Set.of("image/png"));

    private final Detector detector;
    private final DataSize maxFileSize;

    @Autowired
    public FileValidator(FormBridgeProperties properties) {
        this(new DefaultDetector(), properties.upload().maxFileSize());
    }

    public FileValidator(Detector detector, DataSize maxFileSize) {
        this.detector = detector;
        this.maxFileSize = maxFileSize;
    }

    public static List<String> supportedExtensions() {
        return ALLOWED.keySet().stream().sorted().toList();
    }

    public String validate(String fileName, InputStream content, long size) {
        String extension = validateName(fileName);
        validateSize(fileName, size);
        String mediaType = detect(fileName, content);
        if (!ALLOWED.get(extension).contains(mediaType)) {
            throw ApiException.unsupportedMediaType("Zawartość pliku " + fileName + " (" + mediaType
                    + ") nie odpowiada rozszerzeniu ." + extension + ".");
        }
        return mediaType;
    }

    public String validateName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw ApiException.badRequest("Nie przesłano nazwy pliku.");
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw ApiException.badRequest("Nazwa pliku jest dłuższa niż " + MAX_FILE_NAME_LENGTH + " znaków.");
        }
        for (char c : fileName.toCharArray()) {
            if (FORBIDDEN_CHARS.contains(c)) {
                throw ApiException.badRequest("Nazwa pliku zawiera niedozwolony znak „" + c + "”.");
            }
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw ApiException.unsupportedMediaType("Plik " + fileName + " nie ma rozszerzenia. Obsługiwane: "
                    + String.join(", ", supportedExtensions()) + ".");
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.containsKey(extension)) {
            throw ApiException.unsupportedMediaType("Rozszerzenie ." + extension + " nie jest obsługiwane. Obsługiwane: "
                    + String.join(", ", supportedExtensions()) + ".");
        }
        return extension;
    }

    private void validateSize(String fileName, long size) {
        if (size <= 0) {
            throw ApiException.badRequest("Plik " + fileName + " jest pusty.");
        }
        if (size > maxFileSize.toBytes()) {
            throw ApiException.payloadTooLarge("Plik " + fileName + " ma " + size + " bajtów, limit to "
                    + maxFileSize.toBytes() + " bajtów.");
        }
    }

    private String detect(String fileName, InputStream content) {
        try (TikaInputStream in = TikaInputStream.get(new BufferedInputStream(content))) {
            MediaType type = detector.detect(in, new Metadata());
            return type.getBaseType().toString();
        } catch (IOException e) {
            throw ApiException.badRequest("Nie udało się odczytać pliku " + fileName + ".");
        }
    }
}
