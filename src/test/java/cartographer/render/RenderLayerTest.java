package cartographer.render;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderLayerTest {

    @Test
    void defaultsRemainFocusedOnDetailedBaseMap() {
        Set<RenderLayer> layers =
                RenderLayer.defaults();

        assertEquals(
                Set.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.SURFACE,
                        RenderLayer.MARKERS
                ),
                layers
        );

        assertFalse(layers.contains(RenderLayer.SOIL_FERTILITY));
    }

    @Test
    void legacyWaterLayerMapsToSurface() {
        Set<RenderLayer> layers =
                RenderLayer.parse(
                        "terrain,water,markers"
                );

        assertTrue(
                layers.contains(
                        RenderLayer.TERRAIN
                )
        );

        assertTrue(
                layers.contains(
                        RenderLayer.SURFACE
                )
        );

        assertTrue(
                layers.contains(
                        RenderLayer.MARKERS
                )
        );

        assertEquals(
                3,
                layers.size()
        );
    }

    @Test
    void parsesEnvironmentAndGeologyLayers() {
        Set<RenderLayer> layers =
                RenderLayer.parse(
                        "terrain,environment,geology,markers"
                );

        assertEquals(
                Set.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.ENVIRONMENT,
                        RenderLayer.GEOLOGY,
                        RenderLayer.MARKERS
                ),
                layers
        );
    }

    @Test
    void parsingIsCaseInsensitive() {
        Set<RenderLayer> layers =
                RenderLayer.parse(
                        "Terrain,Environment,GEOLOGY"
                );

        assertEquals(
                Set.of(
                        RenderLayer.TERRAIN,
                        RenderLayer.ENVIRONMENT,
                        RenderLayer.GEOLOGY
                ),
                layers
        );
    }

    @Test
    void parsesSoilFertilityLayerCaseInsensitively() {
        assertEquals(
                Set.of(RenderLayer.SOIL_FERTILITY),
                RenderLayer.parse("soil_fertility")
        );
        assertEquals(
                Set.of(RenderLayer.SOIL_FERTILITY),
                RenderLayer.parse("SOIL_FERTILITY")
        );
        assertEquals(
                Set.of(RenderLayer.SOIL_FERTILITY),
                RenderLayer.parse("Soil_Fertility")
        );
    }
}
