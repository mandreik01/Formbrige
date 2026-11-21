package pl.formbridge.app.extract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSlicerTest {

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    @ParameterizedTest(name = "{0}x{1} – wysoki: {2}")
    @CsvSource({
            "1000, 1500, false",
            "1000, 1600, false",
            "1000, 1601, true",
            "1654, 37424, true",
            "2000, 1000, false"
    })
    @DisplayName("Obraz jest wysoki powyżej proporcji 1,6")
    void detectsTallImages(int width, int height, boolean tall) {
        assertThat(ImageSlicer.isTall(image(width, height))).isEqualTo(tall);
    }

    @Test
    @DisplayName("Obraz o zwykłych proporcjach nie jest cięty")
    void keepsRegularImage() {
        BufferedImage img = image(1000, 1400);

        assertThat(ImageSlicer.slice(img)).containsExactly(img);
    }

    @Test
    @DisplayName("Wysoki obraz cięty jest na kawałki o proporcjach A4")
    void slicesTallImageIntoA4Pages() {
        List<BufferedImage> slices = ImageSlicer.slice(image(1000, 5000));

        assertThat(slices).hasSize(4);
        assertThat(slices).allSatisfy(s -> assertThat(s.getWidth()).isEqualTo(1000));
        assertThat(slices.subList(0, 3)).allSatisfy(s -> assertThat(s.getHeight()).isEqualTo(1414));
        assertThat(slices.get(3).getHeight()).isEqualTo(5000 - 3 * 1414);
    }

    @Test
    @DisplayName("Cięcie zachowuje całą wysokość obrazu")
    void preservesFullHeight() {
        List<BufferedImage> slices = ImageSlicer.slice(image(1654, 37424));

        assertThat(slices.stream().mapToInt(BufferedImage::getHeight).sum()).isEqualTo(37424);
        assertThat(slices).hasSize(16);
    }
}
