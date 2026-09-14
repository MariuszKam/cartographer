package cartographer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SurfaceDiscoveryPolicyTest {
    @Test
    void cacheHitDoesNotRequireAnewScanOrScanningState() {
        assertEquals(SurfaceDiscoveryPolicy.Activation.CACHE_HIT,
                SurfaceDiscoveryPolicy.activation(true, false));
    }

    @Test
    void inFlightRequestIsNotDuplicated() {
        assertEquals(SurfaceDiscoveryPolicy.Activation.ALREADY_SCANNING,
                SurfaceDiscoveryPolicy.activation(false, true));
        assertEquals(SurfaceDiscoveryPolicy.Activation.START_SCAN,
                SurfaceDiscoveryPolicy.activation(false, false));
    }

    @Test
    void staleCompletionMustNotBeCached() {
        assertFalse(SurfaceDiscoveryPolicy.shouldCacheCompletion(false));
        assertTrue(SurfaceDiscoveryPolicy.shouldCacheCompletion(true));
    }
}
