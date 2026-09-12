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
                        4,
                        3
                );

        assertEquals(
                80,
                image.getWidth()
        );

        assertEquals(
                154,
                image.getHeight()
        );

        int unavailable =
                image.getRGB(
                        58,
                        25
                );

        int air =
                image.getRGB(
                        58,
                        28
                );

        int granite =
                image.getRGB(
                        58,
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
                        4,
                        4
                );

        int copper =
                image.getRGB(
                        58,
                        26
                );

        int granite =
                image.getRGB(
                        58,
                        30
                );

        assertNotEquals(
                copper,
                granite
        );
    }

    @Test
    void rendersPlayerMarkerWhenProvided() {
        GeologyCrossSection section =
                new GeologyCrossSection(
                        0,
                        0,
                        2,
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
                                                        4,
                                                        true,
                                                        1,
                                                        "rock-granite"
                                                )
                                        )
                                ),
                                new GeologySectionColumn(
                                        1,
                                        1,
                                        0,
                                        List.of(
                                                new GeologySectionRun(
                                                        0,
                                                        4,
                                                        true,
                                                        1,
                                                        "rock-granite"
                                                )
                                        )
                                ),
                                new GeologySectionColumn(
                                        2,
                                        2,
                                        0,
                                        List.of(
                                                new GeologySectionRun(
                                                        0,
                                                        4,
                                                        true,
                                                        1,
                                                        "rock-granite"
                                                )
                                        )
                                )
                        )
                );

        BufferedImage image =
                renderer.render(
                        section,
                        4,
                        4,
                        new GeologySectionMarker(
                                "PLAYER",
                                1,
                                2
                        )
                );

        int markerPixel =
                image.getRGB(
                        56 + 1 * 4 + 2,
                        24 + (4 - 2 - 1) * 4 + 2
                );

        int neighboringGranite =
                image.getRGB(
                        56 + 0 * 4 + 2,
                        24 + (4 - 2 - 1) * 4 + 2
                );

        assertNotEquals(
                neighboringGranite,
                markerPixel
        );
    }
}
