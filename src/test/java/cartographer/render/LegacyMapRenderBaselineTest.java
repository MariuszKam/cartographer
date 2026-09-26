package cartographer.render;

import cartographer.model.HomeState;
import cartographer.progress.ProgressReporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyMapRenderBaselineTest {

    @Test
    void deterministicLegacyRenderMatchesFrozenParityBaseline() {
        RenderedMap rendered = new MapRenderer().render(
                LegacyMapRenderBaselineFixture.center(),
                HomeState.absent(),
                LegacyMapRenderBaselineFixture.chunks(),
                LegacyMapRenderBaselineFixture.options(),
                ProgressReporter.NONE
        );

        assertEquals(
                LegacyMapRenderBaselineFixture.EXPECTED_IMAGE_SIZE,
                rendered.image().getWidth()
        );
        assertEquals(
                LegacyMapRenderBaselineFixture.EXPECTED_IMAGE_SIZE,
                rendered.image().getHeight()
        );
        assertEquals(
                LegacyMapRenderBaselineFixture.EXPECTED_CHUNKS,
                rendered.report().chunks()
        );
        assertEquals(
                LegacyMapRenderBaselineFixture.EXPECTED_TILES_DRAWN,
                rendered.report().tilesDrawn()
        );
        assertEquals(
                LegacyMapRenderBaselineFixture.EXPECTED_IMAGE_FINGERPRINT,
                LegacyMapRenderBaselineFixture.fingerprint(rendered.image())
        );
    }
}
