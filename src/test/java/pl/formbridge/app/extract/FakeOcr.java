package pl.formbridge.app.extract;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

class FakeOcr implements OcrEngine {

    private final List<int[]> sizes = new ArrayList<>();
    private final boolean available;
    private final String text;

    FakeOcr(String text) {
        this(text, true);
    }

    FakeOcr(String text, boolean available) {
        this.text = text;
        this.available = available;
    }

    List<int[]> sizes() {
        return List.copyOf(sizes);
    }

    int calls() {
        return sizes.size();
    }

    @Override
    public String recognize(BufferedImage image) {
        sizes.add(new int[]{image.getWidth(), image.getHeight()});
        return text.replace("{n}", String.valueOf(sizes.size()));
    }

    @Override
    public boolean available() {
        return available;
    }
}
