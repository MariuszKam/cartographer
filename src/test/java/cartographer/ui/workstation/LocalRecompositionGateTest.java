package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderLayer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRecompositionGateTest {

    @Test
    void acceptsOnlyLatestCompletionForSameFrameAndLayers() {
        LocalRecompositionGate gate = new LocalRecompositionGate();
        MapFrame frame = MapFrame.coverage(
                Path.of("world.vcdbs"),
                MapViewportGeometry.fullImage(32, 32, 0, 0, 32, 32)
        );
        Set<RenderLayer> terrain = Set.of(RenderLayer.TERRAIN);

        LocalRecompositionGate.Token first = gate.begin(frame, terrain);
        assertTrue(gate.accepts(first, Optional.of(frame), terrain));

        LocalRecompositionGate.Token second = gate.begin(
                frame,
                Set.of(RenderLayer.TERRAIN, RenderLayer.MARKERS)
        );
        assertFalse(gate.accepts(first, Optional.of(frame), terrain));
        assertTrue(gate.accepts(
                second,
                Optional.of(frame),
                Set.of(RenderLayer.TERRAIN, RenderLayer.MARKERS)
        ));
        assertFalse(gate.accepts(second, Optional.of(frame), terrain));
    }

    @Test
    void invalidationAndFrameReplacementRejectCompletion() {
        LocalRecompositionGate gate = new LocalRecompositionGate();
        MapFrame first = MapFrame.coverage(
                Path.of("first.vcdbs"),
                MapViewportGeometry.fullImage(32, 32, 0, 0, 32, 32)
        );
        MapFrame second = MapFrame.coverage(
                Path.of("second.vcdbs"),
                MapViewportGeometry.fullImage(32, 32, 0, 0, 32, 32)
        );
        Set<RenderLayer> layers = Set.of(RenderLayer.TERRAIN);
        LocalRecompositionGate.Token token = gate.begin(first, layers);

        assertFalse(gate.accepts(token, Optional.of(second), layers));
        gate.invalidate();
        assertFalse(gate.accepts(token, Optional.of(first), layers));
    }
}
