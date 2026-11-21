package pl.formbridge.app;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Samples {

    private Samples() {
    }

    public static String formText() {
        try (InputStream in = Samples.class.getResourceAsStream("/forms/wniosek-zaswiadczenie.txt")) {
            if (in == null) {
                throw new IllegalStateException("Brak pliku testowego wniosek-zaswiadczenie.txt");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] pdf() throws IOException {
        return pdf("Wniosek o wydanie zaswiadczenia");
    }

    public static byte[] pdf(String text) throws IOException {
        return pdfPages(new String[]{text});
    }

    public static byte[] pdfPages(String[] pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.setLeading(16);
                    for (String line : text.split("\n")) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] emptyPdf() throws IOException {
        return emptyPdf(1);
    }

    public static byte[] emptyPdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] docx() throws IOException {
        return docx("Wniosek o wydanie zaświadczenia");
    }

    public static byte[] docx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : text.split("\n")) {
                doc.createParagraph().createRun().setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] formDocx() throws IOException {
        return docx(formText());
    }

    public static byte[] scannedPdf(int pages) throws IOException {
        BufferedImage scan = new BufferedImage(600, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scan.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 850);
        g.setColor(Color.BLACK);
        g.drawString("WNIOSEK O WYDANIE ZASWIADCZENIA", 40, 60);
        g.dispose();
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(doc, scan);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] doc() throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(new byte[]{0x1, 0x2, 0x3, 0x4}), "WordDocument");
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    public static byte[] xlsx() throws IOException {
        return xlsx(new String[]{"Wniosek"});
    }

    public static byte[] xlsx(String[] sheetNames) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String name : sheetNames) {
                Sheet sheet = wb.createSheet(name);
                var header = sheet.createRow(0);
                header.createCell(0).setCellValue("Imię i nazwisko");
                header.createCell(1).setCellValue("PESEL");
                var data = sheet.createRow(1);
                data.createCell(0).setCellValue("Jan Kowalski");
                data.createCell(1).setCellValue("44051401458");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] ods() throws IOException {
        return ods(1);
    }

    public static byte[] ods(int sheets) throws IOException {
        StringBuilder tables = new StringBuilder();
        for (int i = 1; i <= sheets; i++) {
            tables.append("""
                        <table:table table:name="Arkusz%d">
                          <table:table-row>
                            <table:table-cell><text:p>Imię i nazwisko</text:p></table:table-cell>
                            <table:table-cell><text:p>PESEL</text:p></table:table-cell>
                          </table:table-row>
                          <table:table-row>
                            <table:table-cell><text:p>Jan Kowalski %d</text:p></table:table-cell>
                            <table:table-cell><text:p>44051401458</text:p></table:table-cell>
                          </table:table-row>
                        </table:table>
                    """.formatted(i, i));
        }
        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.3">
                  <office:body><office:spreadsheet>
                %s
                  </office:spreadsheet></office:body>
                </office:document-content>
                """.formatted(tables);
        return zipOpenDocument("application/vnd.oasis.opendocument.spreadsheet", content);
    }

    public static byte[] image(String format) throws IOException {
        return image(format, 120, 60);
    }

    public static byte[] png() throws IOException {
        return image("png", 60, 40);
    }

    public static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("Wniosek", 10, Math.min(30, height - 5));
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static byte[] zipOpenDocument(String mime, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            byte[] mimeBytes = mime.getBytes(StandardCharsets.US_ASCII);
            mimetype.setSize(mimeBytes.length);
            mimetype.setCompressedSize(mimeBytes.length);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(mimeBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
