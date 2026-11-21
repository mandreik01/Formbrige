package pl.formbridge.app.extract;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class ImageSlicer {

    static final double A4_RATIO = 1.414;

    static final double TALL_RATIO = 1.6;

    private ImageSlicer() {
    }

    static boolean isTall(BufferedImage image) {
        return image.getHeight() > TALL_RATIO * image.getWidth();
    }

    static List<BufferedImage> slice(BufferedImage image) {
        if (!isTall(image)) {
            return List.of(image);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int sliceHeight = Math.max(1, (int) Math.round(width * A4_RATIO));
        List<BufferedImage> slices = new ArrayList<>();
        for (int y = 0; y < height; y += sliceHeight) {
            int h = Math.min(sliceHeight, height - y);
            slices.add(image.getSubimage(0, y, width, h));
        }
        return List.copyOf(slices);
    }
}
