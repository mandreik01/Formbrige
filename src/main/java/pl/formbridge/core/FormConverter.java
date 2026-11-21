package pl.formbridge.core;

public interface FormConverter {

    String convert(ExtractedDocument document) throws FormConversionException;
}
