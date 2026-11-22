package pl.formbridge.app.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.formbridge.app.config.FormBridgeProperties;
import pl.formbridge.app.extract.TextExtractionService;
import pl.formbridge.app.upload.FileValidator;
import pl.formbridge.core.ExtractedDocument;
import pl.formbridge.core.FormBridgeConverter;
import pl.formbridge.core.FormConversionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/v1/forms")
public class FormsController {

    public static final String HEADER_PROCESSING_MS = "X-FormBridge-Processing-Ms";
    public static final String HEADER_AI_MODEL = "X-FormBridge-AI-Model";
    public static final String HEADER_AI_REFINED = "X-FormBridge-AI-Refined";
    public static final String HEADER_AI_MS = "X-FormBridge-AI-Ms";

    private static final Logger LOG = Logger.getLogger(FormsController.class.getName());
    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FileValidator validator;
    private final TextExtractionService extractor;
    private final FormBridgeConverter converter;
    private final FormBridgeProperties properties;

    public FormsController(FileValidator validator, TextExtractionService extractor,
                           FormBridgeConverter converter, FormBridgeProperties properties) {
        this.validator = validator;
        this.extractor = extractor;
        this.converter = converter;
        this.properties = properties;
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> convert(@RequestPart(name = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nie przesłano pliku w polu „file”.");
        }
        long start = System.nanoTime();
        FormBridgeConverter.Result result = process(file);
        long millis = (System.nanoTime() - start) / 1_000_000;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JSON_UTF8);
        headers.add(HEADER_PROCESSING_MS, Long.toString(millis));
        addAiHeaders(headers, result);
        return new ResponseEntity<>(result.json(), headers, HttpStatus.OK);
    }

    @PostMapping(value = "/convert-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> convertBatch(@RequestPart(name = "files", required = false) List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("Nie przesłano plików w polu „files”.");
        }
        int limit = properties.batch().maxFiles();
        if (files.size() > limit) {
            throw ApiException.badRequest("Przesłano " + files.size() + " plików, limit to " + limit + ".");
        }
        List<BatchEntry> entries = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            entries.add(batchEntry(file));
        }
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(toJson(entries));
    }

    private BatchEntry batchEntry(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        long start = System.nanoTime();
        try {
            if (file.isEmpty()) {
                throw ApiException.badRequest("Plik " + (name.isBlank() ? "bez nazwy" : name) + " jest pusty.");
            }
            FormBridgeConverter.Result result = process(file);
            long millis = (System.nanoTime() - start) / 1_000_000;
            return BatchEntry.success(name, millis, parseJson(result.json()), result.proposedDictionaries());
        } catch (ApiException e) {
            return batchError(name, e.getStatus().value(), e.getTitle(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, e, () -> "Nie udało się przetworzyć pliku " + name + " we wsadzie");
            return batchError(name, 500, "Błąd przetwarzania", "Nie udało się przetworzyć pliku.");
        }
    }

    private static BatchEntry batchError(String name, int status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        return BatchEntry.error(name, status, problem);
    }

    private static JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nie udało się zdeserializować wyniku konwersji", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nie udało się przygotować odpowiedzi JSON", e);
        }
    }

    private FormBridgeConverter.Result process(MultipartFile file) {
        String name = file.getOriginalFilename();
        String mediaType;
        try (InputStream in = file.getInputStream()) {
            mediaType = validator.validate(name, in, file.getSize());
        } catch (IOException e) {
            throw ApiException.badRequest("Nie udało się odczytać przesłanego pliku.");
        }
        ExtractedDocument document;
        try (InputStream in = file.getInputStream()) {
            document = extractor.extract(name, mediaType, in);
        } catch (IOException e) {
            throw ApiException.badRequest("Nie udało się odczytać przesłanego pliku.");
        }
        try {
            return converter.convertDetailed(document);
        } catch (FormConversionException e) {
            throw ApiException.unprocessable(e.getMessage());
        }
    }

    private static void addAiHeaders(HttpHeaders headers, FormBridgeConverter.Result result) {
        if (result.ai() == null || result.ai().model() == null || result.ai().model().isEmpty()) {
            return;
        }
        headers.add(HEADER_AI_MODEL, result.ai().model());
        headers.add(HEADER_AI_REFINED, result.ai().refined() + "/" + result.ai().candidates());
        headers.add(HEADER_AI_MS, Long.toString(result.ai().millis()));
    }

    private record BatchEntry(String fileName, int status, Long processingMs, JsonNode form,
                              List<String> proposedDictionaries, ProblemDetail error) {
        static BatchEntry success(String fileName, long processingMs, JsonNode form, List<String> proposedDictionaries) {
            return new BatchEntry(fileName, 200, processingMs, form, proposedDictionaries, null);
        }

        static BatchEntry error(String fileName, int status, ProblemDetail error) {
            return new BatchEntry(fileName, status, null, null, null, error);
        }
    }
}
