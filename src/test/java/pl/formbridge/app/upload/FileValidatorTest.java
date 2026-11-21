package pl.formbridge.app.upload;

import org.apache.tika.detect.DefaultDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import pl.formbridge.app.Samples;
import pl.formbridge.app.web.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator(new DefaultDetector(), DataSize.ofMegabytes(20));

    private String validate(String name, byte[] content) {
        return validator.validate(name, new ByteArrayInputStream(content), content.length);
    }

    private static int statusOf(ThrowingCall call) {
        ApiException e = catchThrowableOfType(ApiException.class, call::run);
        assertThat(e).as("oczekiwano ApiException").isNotNull();
        return e.getStatus().value();
    }

    private interface ThrowingCall {
        void run() throws Exception;
    }

    @Test
    @DisplayName("PDF przechodzi walidację")
    void acceptsPdf() throws IOException {
        assertThat(validate("wniosek.pdf", Samples.pdf())).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("DOCX przechodzi walidację")
    void acceptsDocx() throws IOException {
        assertThat(validate("wniosek.docx", Samples.docx()))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    @DisplayName("DOC (kontener OLE2) przechodzi walidację")
    void acceptsDoc() throws IOException {
        assertThat(validate("wniosek.doc", Samples.doc()))
                .isIn("application/msword", "application/x-tika-msoffice");
    }

    @Test
    @DisplayName("Plik .DOC, który w środku jest DOCX, przechodzi walidację")
    void acceptsDocWithDocxContent() throws IOException {
        assertThat(validate("WNIOSEK.DOC", Samples.docx()))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    @DisplayName("XLSX przechodzi walidację")
    void acceptsXlsx() throws IOException {
        assertThat(validate("tabela.xlsx", Samples.xlsx()))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    @DisplayName("ODS przechodzi walidację")
    void acceptsOds() throws IOException {
        assertThat(validate("tabela.ods", Samples.ods()))
                .isEqualTo("application/vnd.oasis.opendocument.spreadsheet");
    }

    @ParameterizedTest(name = "rozszerzenie .{0} dla JPEG")
    @ValueSource(strings = {"jpg", "jpeg", "jpe", "JPG", "JPE"})
    @DisplayName("Wszystkie warianty rozszerzeń JPEG, także .JPE i wielkie litery")
    void acceptsJpegVariants(String extension) throws IOException {
        assertThat(validate("skan." + extension, Samples.image("jpg"))).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("PNG przechodzi walidację, również z wielkimi literami w rozszerzeniu")
    void acceptsPng() throws IOException {
        assertThat(validate("skan.PNG", Samples.image("png"))).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Nazwa 250 znaków jest dozwolona, 251 znaków już nie")
    void nameLengthBoundary() throws IOException {
        byte[] pdf = Samples.pdf();
        String name250 = "a".repeat(250 - 4) + ".pdf";
        assertThat(name250).hasSize(250);
        assertThat(validate(name250, pdf)).isEqualTo("application/pdf");

        String name251 = "a".repeat(251 - 4) + ".pdf";
        assertThat(statusOf(() -> validate(name251, pdf))).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @ParameterizedTest(name = "znak {0} w nazwie jest zabroniony")
    @ValueSource(strings = {";", ":", ">", "<", "/", "\\", "|", "'", "*", "%", "$"})
    @DisplayName("Zabronione znaki w nazwie pliku kończą się kodem 400")
    void rejectsForbiddenCharacters(String character) throws IOException {
        byte[] pdf = Samples.pdf();
        assertThat(statusOf(() -> validate("wnio" + character + "sek.pdf", pdf)))
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Pusty plik kończy się kodem 400")
    void rejectsEmptyFile() {
        assertThat(statusOf(() -> validate("wniosek.pdf", new byte[0]))).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Brak nazwy pliku kończy się kodem 400")
    void rejectsMissingName() {
        assertThat(statusOf(() -> validate("   ", new byte[]{1}))).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Nieobsługiwane rozszerzenie kończy się kodem 415")
    void rejectsUnsupportedExtension() throws IOException {
        byte[] pdf = Samples.pdf();
        assertThat(statusOf(() -> validate("wniosek.exe", pdf))).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(statusOf(() -> validate("wniosek", pdf))).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    }

    @Test
    @DisplayName("MIME niezgodny z rozszerzeniem kończy się kodem 415")
    void rejectsMismatchedContent() throws IOException {
        byte[] png = Samples.image("png");
        assertThat(statusOf(() -> validate("wniosek.pdf", png))).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    }

    @Test
    @DisplayName("Plik większy niż limit kończy się kodem 413")
    void rejectsTooLargeFile() throws IOException {
        FileValidator small = new FileValidator(new DefaultDetector(), DataSize.ofBytes(100));
        byte[] pdf = Samples.pdf();
        ApiException e = catchThrowableOfType(ApiException.class,
                () -> small.validate("wniosek.pdf", new ByteArrayInputStream(pdf), pdf.length));
        assertThat(e.getStatus().value()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    @Test
    @DisplayName("Lista obsługiwanych rozszerzeń jest kompletna")
    void listsSupportedExtensions() {
        assertThat(FileValidator.supportedExtensions())
                .containsExactly("doc", "docx", "jpe", "jpeg", "jpg", "ods", "pdf", "png", "xlsx");
    }

    @Test
    @DisplayName("Komunikaty błędów są po polsku")
    void messagesInPolish() {
        assertThatThrownBy(() -> validate("wniosek;1.pdf", new byte[]{1}))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("niedozwolony znak");
    }
}
