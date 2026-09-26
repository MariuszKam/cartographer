package cartographer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
