package cartographer.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SurfaceDiscoveryRequestGateTest {
    @Test
    void olderRadiusResultCannotReplaceCurrentRequest() {
        SurfaceDiscoveryRequestGate gate = new SurfaceDiscoveryRequestGate();
        var save = Path.of("world.vcdbs");
        var first = gate.begin(new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(save, 256));
        var secondKey = new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(save, 512);
        var second = gate.begin(secondKey);

        assertFalse(gate.accepts(first, first.key()));
        assertTrue(gate.accepts(second, secondKey));
    }

    @Test
    void previousSaveResultIsRejected() {
        SurfaceDiscoveryRequestGate gate = new SurfaceDiscoveryRequestGate();
        var old = gate.begin(new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(Path.of("old.vcdbs"), 256));
        var current = new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(Path.of("new.vcdbs"), 256);
        gate.begin(current);

        assertFalse(gate.accepts(old, old.key()));
    }

    @Test
    void currentGenerationAndKeyAreAccepted() {
        SurfaceDiscoveryRequestGate gate = new SurfaceDiscoveryRequestGate();
        var key = new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(Path.of("world.vcdbs"), 256);
        var token = gate.begin(key);

        assertTrue(gate.accepts(token, key));
    }
}
