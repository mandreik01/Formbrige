package pl.formbridge.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.io.Serial;

public class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient HttpStatusCode status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Niepoprawne żądanie", detail);
    }

    public static ApiException payloadTooLarge(String detail) {
        return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "Plik jest za duży", detail);
    }

    public static ApiException unsupportedMediaType(String detail) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nieobsługiwany format pliku", detail);
    }

    public static ApiException unprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Nie udało się przetworzyć formularza", detail);
    }
}
