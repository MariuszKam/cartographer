package cartographer.render;

import cartographer.model.HomeState;
import cartographer.model.WorldPosition;
import cartographer.resource.SurfaceMaterialAnalysis;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SurfaceResourceOverlayRendererTest {

    @Test
    void explicitMarkerFlagControlsSystemMarkers() {
        SurfaceResourceOverlayRenderer renderer =
                new SurfaceResourceOverlayRenderer();
        SurfaceMaterialAnalysis analysis =
                new SurfaceMaterialAnalysis("Test", 0, List.of(), List.of());
        WorldPosition center = new WorldPosition(16, 64, 16);
        BufferedImage withoutMarkers =
                new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage withMarkers =
                new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        renderer.drawMaterial(
                withoutMarkers,
                center,
                16,
                analysis,
                center,
                HomeState.absent(),
                false
        );
        renderer.drawMaterial(
                withMarkers,
                center,
                16,
                analysis,
                center,
                HomeState.absent(),
                true
        );

        assertNotEquals(
                withoutMarkers.getRGB(32, 32),
                withMarkers.getRGB(32, 32)
        );
    }
}
