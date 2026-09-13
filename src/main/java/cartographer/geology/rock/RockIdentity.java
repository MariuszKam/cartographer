package cartographer.geology.rock;

import java.util.Objects;

public record RockIdentity(
        int blockId,
        String code,
        String namespace,
        String rockName
) {
    public RockIdentity {
        if (blockId < 0) {
            throw new IllegalArgumentException("Rock block ID must be non-negative");
        }
        Objects.requireNonNull(code, "Rock code is required");
        Objects.requireNonNull(namespace, "Rock namespace is required");
        Objects.requireNonNull(rockName, "Rock name is required");
        if (code.isBlank() || rockName.isBlank()) {
            throw new IllegalArgumentException("Rock identity values must not be blank");
        }
    }
}
