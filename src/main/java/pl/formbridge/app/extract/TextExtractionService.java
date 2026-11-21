package pl.formbridge.app.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Service;
import pl.formbridge.app.web.ApiException;
import pl.formbridge.core.ExtractedDocument;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class TextExtractionService {

    static final int MIN_CHARS_PER_PAGE = 50;

    private static final int OCR_DPI = 200;
    private static final Logger LOG = Logger.getLogger(TextExtractionService.class.getName());

    private final OcrEngine ocr;

    TextExtractionService(OcrEngine ocr) {
        this.ocr = ocr;
    }

    public ExtractedDocument extract(String fileName, String mediaType, InputStream content) {
        byte[] bytes = readAll(fileName, content);
        Extraction extraction = switch (mediaType) {
            case "application/pdf" -> fromPdf(fileName, bytes);
            case "image/jpeg", "image/png" -> new Extraction(fromImage(fileName, bytes), true);
            default -> new Extraction(parse(fileName, bytes), false);
        };
        List<String> pages = extraction.pages();
        if (pages.isEmpty()) {
            throw ApiException.unprocessable("Plik " + fileName
                    + " nie zawiera tekstu możliwego do odczytania. Dla skanów upewnij się, że obraz jest czytelny.");
        }
        return new ExtractedDocument(fileName, mediaType, pages, extraction.ocrUsed());
    }

    private Extraction fromPdf(String fileName, byte[] bytes) {
        List<String> pages = parse(fileName, bytes);
        if (hasTextLayer(pages)) {
            return new Extraction(pages, false);
        }
        List<String> scanned = ocrPdf(fileName, bytes);
        return scanned.isEmpty() ? new Extraction(pages, false) : new Extraction(scanned, true);
    }

    private static boolean hasTextLayer(List<String> pages) {
        if (pages.isEmpty()) {
            return false;
        }
        long chars = pages.stream().mapToLong(p -> p.replaceAll("\\s", "").length()).sum();
        return chars >= (long) MIN_CHARS_PER_PAGE * pages.size();
    }

    private List<String> ocrPdf(String fileName, byte[] bytes) {
        if (!ocr.available()) {
            LOG.warning(() -> "Brak Tesseracta, pomijam OCR pliku " + fileName);
            return List.of();
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pages = new ArrayList<>(document.getNumberOfPages());
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                String text = ocr.recognize(renderer.renderImageWithDPI(i, OCR_DPI, ImageType.GRAY)).strip();
                if (!text.isEmpty()) {
                    pages.add(text);
                }
            }
            return List.copyOf(pages);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Nie udało się wyrenderować stron PDF " + fileName, e);
            return List.of();
        }
    }

    private List<String> fromImage(String fileName, byte[] bytes) {
        if (!ocr.available()) {
            throw ApiException.unprocessable("Odczyt pliku " + fileName
                    + " wymaga OCR, który jest niedostępny w tym środowisku.");
        }
        BufferedImage image = readImage(fileName, bytes);
        List<String> pages = new ArrayList<>();
        for (BufferedImage slice : ImageSlicer.slice(image)) {
            String text = ocr.recognize(slice).strip();
            if (!text.isEmpty()) {
                pages.add(text);
            }
        }
        return List.copyOf(pages);
    }

    private List<String> parse(String fileName, byte[] bytes) {
        PageContentHandler handler = new PageContentHandler();
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            new AutoDetectParser().parse(in, handler, new Metadata(), noTikaOcr());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tika nie odczytała pliku " + fileName, e);
            throw ApiException.unprocessable("Nie udało się odczytać treści pliku " + fileName + ".");
        }
        return handler.pages();
    }

    private static ParseContext noTikaOcr() {
        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdf);
        context.set(Parser.class, new AutoDetectParser(new DefaultParser(
                MediaTypeRegistry.getDefaultRegistry(), new ServiceLoader(), Set.of(TesseractOCRParser.class))));
        return context;
    }

    private static BufferedImage readImage(String fileName, byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw ApiException.unprocessable("Nie udało się odczytać obrazu " + fileName + ".");
            }
            return image;
        } catch (IOException e) {
            throw ApiException.unprocessable("Nie udało się odczytać obrazu " + fileName + ".");
        }
    }

    private static byte[] readAll(String fileName, InputStream content) {
        try {
            return content.readAllBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("Nie udało się odczytać pliku " + fileName + ".");
        }
    }

    private record Extraction(List<String> pages, boolean ocrUsed) {
    }
}
