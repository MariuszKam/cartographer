package cartographer.ui.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SurfaceObjectDiscoveryStateTest {
    @Test
    void onlyReadyWithSelectionAllowsObjectRendering() {
        assertAllowsRender(SurfaceObjectDiscoveryState.READY, false, true, true);
        assertAllowsRender(SurfaceObjectDiscoveryState.READY, true, true, false);
        assertAllowsRender(SurfaceObjectDiscoveryState.READY, false, false, false);
        assertAllowsRender(SurfaceObjectDiscoveryState.EMPTY, false, true, false);
        assertAllowsRender(SurfaceObjectDiscoveryState.FAILED, false, true, false);
        assertAllowsRender(SurfaceObjectDiscoveryState.NOT_SCANNED, false, true, false);
        assertAllowsRender(SurfaceObjectDiscoveryState.SCANNING, false, true, false);
    }

    @Test
    void multipleObjectRenderAvailabilityFollowsSelectionCount() {
        assertAllowsRenderForSelectionCount(2, true);
        assertAllowsRenderForSelectionCount(0, false);
    }

    @Test
    void emptyAndFailedRemainCurrentOnlyForTheSameKey() {
        assertCurrentFor(SurfaceObjectDiscoveryState.EMPTY, true, true);
        assertCurrentFor(SurfaceObjectDiscoveryState.FAILED, true, true);
        assertCurrentFor(SurfaceObjectDiscoveryState.READY, true, true);
        assertCurrentFor(SurfaceObjectDiscoveryState.EMPTY, false, false);
        assertCurrentFor(SurfaceObjectDiscoveryState.NOT_SCANNED, true, false);
        assertCurrentFor(SurfaceObjectDiscoveryState.SCANNING, true, false);
    }

    private static void assertAllowsRender(
            SurfaceObjectDiscoveryState state,
            boolean globallyBusy,
            boolean hasSelection,
            boolean expected
    ) {
        assertEquals(expected, state.allowsRender(globallyBusy, hasSelection));
    }

    private static void assertAllowsRenderForSelectionCount(
            int selectedResources,
            boolean expected
    ) {
        assertAllowsRender(
                SurfaceObjectDiscoveryState.READY,
                false,
                selectedResources > 0,
                expected
        );
    }

    private static void assertCurrentFor(
            SurfaceObjectDiscoveryState state,
            boolean keyMatches,
            boolean expected
    ) {
        assertEquals(expected, state.isCurrentFor(keyMatches));
    }
}
