package cartographer.render;

import cartographer.model.WorldPosition;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapCell;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ActualOreOverlayPainterTest {

    @Test
    void paintsOverlayOntoExpectedWorldColumnPixel() {
        BufferedImage image =
                new BufferedImage(
                        20,
                        20,
                        BufferedImage.TYPE_INT_ARGB
                );

        int baseColor =
                new Color(
                        20,
                        80,
                        40
                ).getRGB();

        for (int y = 0;
             y < image.getHeight();
             y++) {

            for (int x = 0;
                 x < image.getWidth();
                 x++) {

                image.setRGB(
                        x,
                        y,
                        baseColor
                );
            }
        }

        ActualBlockMap map =
                new ActualBlockMap(
                        "nativecopper",
                        10,
                        10,
                        10,
                        3,
                        5,
                        7,
                        List.of(
                                new ActualBlockMapCell(
                                        10,
                                        10,
                                        3,
                                        5,
                                        7
                                )
                        )
                );

        new ActualOreOverlayPainter().paint(
                image,
                map,
                new WorldPosition(
                        10.0,
                        100.0,
                        10.0
                ),
                10
        );

        assertNotEquals(
                baseColor,
                image.getRGB(
                        10,
                        10
                )
        );
    }
}
