package pl.formbridge.app.extract;

import java.awt.image.BufferedImage;

public interface OcrEngine {

    String recognize(BufferedImage image);

    boolean available();
}
