package cartographer.render;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void removedWaterAliasIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RenderLayer.parse("terrain,water,markers")
        );

        assertEquals(
                "Unknown render layer: water",
                exception.getMessage()
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
