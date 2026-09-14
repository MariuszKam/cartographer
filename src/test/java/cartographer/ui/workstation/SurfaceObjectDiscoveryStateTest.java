package cartographer.ui.workstation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceObjectDiscoveryStateTest {
    @Test
    void onlyReadyWithSelectionAllowsObjectRendering() {
        assertTrue(SurfaceObjectDiscoveryState.READY.allowsRender(false, true));
        assertFalse(SurfaceObjectDiscoveryState.READY.allowsRender(true, true));
        assertFalse(SurfaceObjectDiscoveryState.READY.allowsRender(false, false));
        assertFalse(SurfaceObjectDiscoveryState.EMPTY.allowsRender(false, true));
        assertFalse(SurfaceObjectDiscoveryState.FAILED.allowsRender(false, true));
        assertFalse(SurfaceObjectDiscoveryState.NOT_SCANNED.allowsRender(false, true));
        assertFalse(SurfaceObjectDiscoveryState.SCANNING.allowsRender(false, true));
    }

    @Test
    void emptyAndFailedRemainCurrentOnlyForTheSameKey() {
        assertTrue(SurfaceObjectDiscoveryState.EMPTY.isCurrentFor(true));
        assertTrue(SurfaceObjectDiscoveryState.FAILED.isCurrentFor(true));
        assertTrue(SurfaceObjectDiscoveryState.READY.isCurrentFor(true));
        assertFalse(SurfaceObjectDiscoveryState.EMPTY.isCurrentFor(false));
        assertFalse(SurfaceObjectDiscoveryState.NOT_SCANNED.isCurrentFor(true));
        assertFalse(SurfaceObjectDiscoveryState.SCANNING.isCurrentFor(true));
    }
}
