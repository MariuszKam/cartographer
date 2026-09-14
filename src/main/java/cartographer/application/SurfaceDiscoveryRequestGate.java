package cartographer.application;

import java.nio.file.Path;
import java.util.Objects;

/** Generation/key guard preventing stale asynchronous discovery results. */
public final class SurfaceDiscoveryRequestGate {
    private long generation;
    private SurfaceDiscoveryKey currentKey;

    public SurfaceDiscoveryToken begin(SurfaceDiscoveryKey key) {
        currentKey = Objects.requireNonNull(key, "key is required");
        return new SurfaceDiscoveryToken(++generation, key);
    }

    public void invalidate() {
        generation++;
        currentKey = null;
    }

    public boolean accepts(SurfaceDiscoveryToken token, SurfaceDiscoveryKey key) {
        return token != null
                && token.generation() == generation
                && token.key().equals(key)
                && token.key().equals(currentKey);
    }

    public record SurfaceDiscoveryKey(Path savePath, int radius) {
        public SurfaceDiscoveryKey {
            Objects.requireNonNull(savePath, "savePath is required");
            if (radius <= 0) {
                throw new IllegalArgumentException("radius must be positive");
            }
        }
    }

    public record SurfaceDiscoveryToken(long generation, SurfaceDiscoveryKey key) {
        public SurfaceDiscoveryToken {
            Objects.requireNonNull(key, "key is required");
        }
    }
}
