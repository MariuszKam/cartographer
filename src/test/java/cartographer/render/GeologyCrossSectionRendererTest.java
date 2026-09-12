package cartographer.render;

import cartographer.geology.crosssection.GeologyCrossSection;
import cartographer.geology.crosssection.GeologySectionColumn;
import cartographer.geology.crosssection.GeologySectionRun;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GeologyCrossSectionRendererTest {

    private final GeologyCrossSectionRenderer renderer =
            new GeologyCrossSectionRenderer();

    @Test
    void rendersVerticalRunsTopDown() {
        GeologyCrossSection section =
                new GeologyCrossSection(
                        0,
                        0,
                        0,
                        0,
                        0,
                        4,
                        List.of(
                                new GeologySectionColumn(
                                        0,
                                        0,
                                        0,
                                        List.of(
                                                new GeologySectionRun(
                                                        0,
                                                        2,
                                                        true,
                                                        1,
                                                        "rock-granite"
                                                ),
                                                new GeologySectionRun(
                                                        2,
                                                        3,
                                                        true,
                                                        0,
                                                        "air"
                                                ),
                                                new GeologySectionRun(
                                                        3,
                                                        4,
                                                        false,
                                                        -1,
                                                        "unavailable"
                                                )
                                        )
                                )
                        )
                );

        BufferedImage image =
                renderer.render(
                        section,
                        2,
                        3
                );

        assertEquals(
                78,
                image.getWidth()
        );

        assertEquals(
                80,
                image.getHeight()
        );

        int unavailable =
                image.getRGB(
                        56,
                        25
                );

        int air =
                image.getRGB(
                        56,
                        28
                );

        int granite =
                image.getRGB(
                        56,
                        31
                );

        assertNotEquals(
                unavailable,
                air
        );

        assertNotEquals(
                air,
                granite
        );

        assertNotEquals(
                unavailable,
                granite
        );
    }

    @Test
    void copperOreIsVisuallyDistinctFromHostRock() {
        GeologyCrossSection section =
                new GeologyCrossSection(
                        0,
                        0,
                        0,
                        0,
                        0,
                        2,
                        List.of(
                                new GeologySectionColumn(
                                        0,
                                        0,
                                        0,
                                        List.of(
                                                new GeologySectionRun(
                                                        0,
                                                        1,
                                                        true,
                                                        1,
                                                        "rock-granite"
                                                ),
                                                new GeologySectionRun(
                                                        1,
                                                        2,
                                                        true,
                                                        2,
                                                        "ore-poor-nativecopper-granite"
                                                )
                                        )
                                )
                        )
                );

        BufferedImage image =
                renderer.render(
                        section,
                        2,
                        4
                );

        int copper =
                image.getRGB(
                        56,
                        26
                );

        int granite =
                image.getRGB(
                        56,
                        30
                );

        assertNotEquals(
                copper,
                granite
        );
    }
}
