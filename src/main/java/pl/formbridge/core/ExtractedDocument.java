package pl.formbridge.core;

import java.util.List;
import java.util.Objects;

public record ExtractedDocument(String fileName, String mediaType, List<String> pages, boolean ocrUsed) {

    public ExtractedDocument {
        Objects.requireNonNull(pages, "pages");
        pages = List.copyOf(pages);
    }

    public String text() {
        return String.join("\f", pages);
    }
}
