package pl.formbridge.core;

public class FormConversionException extends Exception {

    private static final long serialVersionUID = 1L;

    public FormConversionException(String message) {
        super(message);
    }

    public FormConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
