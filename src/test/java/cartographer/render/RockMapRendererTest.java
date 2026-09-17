package cartographer.render;

import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;
import cartographer.geology.rock.RockMap;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RockMapRendererTest {
    private static final RockIdentity GRANITE = new RockIdentity(
            1,
            "game:rock-granite",
            "game",
            "granite"
    );
    private static final RockIdentity SHALE = new RockIdentity(
            2,
            "game:rock-shale",
            "game",
            "shale"
    );
    private static final RockIdentity MODDED = new RockIdentity(
            3,
            "geologymod:rock-gneiss",
            "geologymod",
            "gneiss"
    );

    @Test
    void rendersCategoricalStatesWithExpectedDimensions() {
        RockMapRenderResult result = new RockMapRenderer().render(
                map(
                        10,
                        20,
                        2,
                        RockColumnSample.observed(10, 20, GRANITE, 5),
                        RockColumnSample.noRock(11, 20),
                        RockColumnSample.unavailable(10, 21)
                )
        );

        assertEquals(5, result.image().getWidth());
        assertEquals(5, result.image().getHeight());
        assertEquals(5, result.geometry().imageWidth());
        assertEquals(8.0, result.geometry().worldMinX());
        assertEquals(18.0, result.geometry().worldMinZ());
        assertEquals(13.0, result.geometry().worldMaxXExclusive());
        assertEquals(23.0, result.geometry().worldMaxZExclusive());
        assertEquals(0.0, result.geometry().absoluteWorldXToImageX(8));
        assertEquals(1.0, result.geometry().absoluteWorldXToImageX(9));
        assertEquals(5.0, result.geometry().absoluteWorldXToImageX(13));
        assertEquals(
                new RockPalette().colorFor(GRANITE),
                result.image().getRGB(2, 2)
        );
        assertNotEquals(
                result.image().getRGB(3, 2),
                result.image().getRGB(2, 3)
        );
        assertEquals(0, result.image().getRGB(0, 0));
    }

    @Test
    void paletteAndLegendAreDeterministicAndOrderedByCount() {
        RockMap map = map(
                0,
                0,
                2,
                RockColumnSample.observed(0, 0, MODDED, 1),
                RockColumnSample.observed(1, 0, SHALE, 1),
                RockColumnSample.observed(0, 1, SHALE, 1),
                RockColumnSample.observed(1, 1, GRANITE, 1)
        );

        RockMapRenderResult first = new RockMapRenderer().render(map);
        RockMapRenderResult second = new RockMapRenderer().render(map);

        assertEquals(first.legend(), second.legend());
        assertEquals(
                List.of(
                        "game:rock-shale",
                        "game:rock-granite",
                        "geologymod:rock-gneiss"
                ),
                first.legend().stream()
                        .map(entry -> entry.rock().code())
                        .toList()
        );
        assertEquals(
                List.of(2L, 1L, 1L),
                first.legend().stream()
                        .map(RockLegendEntry::observedCellCount)
                        .toList()
        );
        assertEquals(
                List.of(50.0, 25.0, 25.0),
                first.legend().stream()
                        .map(RockLegendEntry::observedPercentage)
                        .toList()
        );
        assertNotEquals(
                new RockPalette().colorFor(GRANITE),
                new RockPalette().colorFor(MODDED)
        );
        assertEquals(4, first.observedCount());
    }

    @Test
    void floorsFractionalNegativeCenterForPixelMapping() {
        RockMap map = new RockMap(
                new WorldPosition(-0.2, 0, -0.2),
                1,
                List.of(RockColumnSample.observed(-1, -1, MODDED, 1))
        );

        RockMapRenderResult result = new RockMapRenderer().render(map);

        assertEquals(
                new RockPalette().colorFor(MODDED),
                result.image().getRGB(1, 1)
        );
        assertEquals(-2.0, result.geometry().worldMinX());
        assertEquals(-2.0, result.geometry().worldMinZ());
        assertEquals(1.0, result.geometry().worldMaxXExclusive());
        assertEquals(1.0, result.geometry().worldMaxZExclusive());
    }

    @Test
    void floorsFractionalPositiveCenterForPixelMapping() {
        RockMap map = new RockMap(
                new WorldPosition(10.8, 0, 10.8),
                1,
                List.of(RockColumnSample.observed(10, 10, GRANITE, 1))
        );

        RockMapRenderResult result = new RockMapRenderer().render(map);

        assertEquals(
                new RockPalette().colorFor(GRANITE),
                result.image().getRGB(1, 1)
        );
    }

    @Test
    void fallbackColorIncludesNamespaceAndFullCode() {
        RockIdentity otherNamespace = new RockIdentity(
                3,
                "othermod:rock-gneiss",
                "othermod",
                "gneiss"
        );

        assertNotEquals(
                new RockPalette().colorFor(MODDED),
                new RockPalette().colorFor(otherNamespace)
        );
    }

    @Test
    void legendCountsAllStatesSeparately() {
        RockMapRenderResult result = new RockMapRenderer().render(
                map(
                        0,
                        0,
                        1,
                        RockColumnSample.observed(0, 0, GRANITE, 1),
                        RockColumnSample.noRock(1, 0),
                        RockColumnSample.unavailable(0, 1)
                )
        );

        assertEquals(1, result.observedCount());
        assertEquals(1, result.noRockCount());
        assertEquals(1, result.unavailableCount());
        assertEquals(1, result.legend().size());
        assertEquals(100.0, result.legend().get(0).observedPercentage());
    }

    @Test
    void preservesCircleBackgroundAndRendersBothUnavailableParityColors() {
        RockMapRenderResult result = new RockMapRenderer().render(
                map(
                        0,
                        0,
                        2,
                        RockColumnSample.observed(0, 0, GRANITE, 5),
                        RockColumnSample.noRock(1, 0),
                        RockColumnSample.unavailable(0, 1),
                        RockColumnSample.unavailable(1, 1)
                )
        );

        assertEquals(new RockPalette().colorFor(GRANITE), result.image().getRGB(2, 2));
        assertEquals(0xFF4A4A4A, result.image().getRGB(3, 2));
        assertEquals(0xFF707070, result.image().getRGB(2, 3));
        assertEquals(0xFF888888, result.image().getRGB(3, 3));
        assertEquals(0, result.image().getRGB(0, 0));
        assertEquals(1, result.observedCount());
        assertEquals(1, result.noRockCount());
        assertEquals(2, result.unavailableCount());
    }

    private RockMap map(
            int centerX,
            int centerZ,
            int radius,
            RockColumnSample... samples
    ) {
        return new RockMap(
                new WorldPosition(centerX, 0, centerZ),
                radius,
                List.of(samples)
        );
    }
}
