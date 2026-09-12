package cartographer.render;

import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapCell;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ActualBlockMapRendererTest {

    @Test
    void rendersNonEmptyActualBlockMap() {
        ActualBlockMap map =
                new ActualBlockMap(
                        "copper",
                        100,
                        200,
                        4,
                        6,
                        2,
                        20,
                        List.of(
                                new ActualBlockMapCell(
                                        100,
                                        200,
                                        3,
                                        2,
                                        4
                                ),
                                new ActualBlockMapCell(
                                        101,
                                        200,
                                        2,
                                        10,
                                        20
                                )
                        )
                );

        BufferedImage image =
                new ActualBlockMapRenderer().render(
                        map,
                        4
                );

        assertTrue(
                image.getWidth() > 0
        );

        assertTrue(
                image.getHeight() > 0
        );
    }
}
