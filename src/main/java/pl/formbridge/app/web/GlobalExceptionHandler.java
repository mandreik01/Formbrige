package pl.formbridge.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import pl.formbridge.core.FormConversionException;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        return problem(e.getStatus().value(), e.getTitle(), e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ProblemDetail handleMissingInput(Exception e) {
        return problem(HttpStatus.BAD_REQUEST.value(), "Niepoprawne żądanie",
                "Żądanie nie zawiera wymaganych danych (plik w polu „file”/„files” albo poprawne pola JSON).");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE.value(), "Plik jest za duży",
                "Przesłany plik przekracza dopuszczalny rozmiar.");
    }

    @ExceptionHandler(FormConversionException.class)
    ProblemDetail handleConversion(FormConversionException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT.value(), "Nie udało się przetworzyć formularza", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleOther(Exception e) {
        LOG.log(Level.SEVERE, "Nieoczekiwany błąd przetwarzania żądania", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Błąd serwera",
                "Wystąpił nieoczekiwany błąd podczas przetwarzania żądania.");
    }

    private static ProblemDetail problem(int status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("https://formbridge.pl/problem/" + status));
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }
}
