package cartographer.application;

/** Pure lifecycle decisions shared by the discovery orchestration and tests. */
public final class SurfaceDiscoveryPolicy {
    private SurfaceDiscoveryPolicy() {
    }

    public enum Activation {
        CACHE_HIT,
        START_SCAN,
        ALREADY_SCANNING
    }

    public static Activation activation(boolean cacheHit, boolean sameKeyScanInFlight) {
        if (cacheHit) {
            return Activation.CACHE_HIT;
        }
        return sameKeyScanInFlight ? Activation.ALREADY_SCANNING : Activation.START_SCAN;
    }

    public static boolean shouldCacheCompletion(boolean tokenAccepted) {
        return tokenAccepted;
    }
}
