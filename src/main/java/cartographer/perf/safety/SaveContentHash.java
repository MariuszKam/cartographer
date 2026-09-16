package cartographer.perf.safety;

import java.util.Objects;

/** Full lowercase hexadecimal SHA-256 digest of a protected file. */
public record SaveContentHash(String sha256Hex) {
    public SaveContentHash {
        Objects.requireNonNull(sha256Hex, "sha256Hex is required");
        if (!sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "sha256Hex must be exactly 64 lowercase hexadecimal characters"
            );
        }
    }
}
