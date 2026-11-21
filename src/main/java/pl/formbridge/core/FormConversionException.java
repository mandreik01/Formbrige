package pl.formbridge.core;

public class FormConversionException extends Exception {

    public FormConversionException(String message) {
        super(message);
    }

    public FormConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
