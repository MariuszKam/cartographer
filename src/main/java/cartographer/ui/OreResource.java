package cartographer.ui;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record OreResource(
        String displayName,
        String match,
        String sourceKey,
        boolean registryVerified,
        int registryMatchCount
) {

    public OreResource {
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(sourceKey, "sourceKey is required");

        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (match.isBlank()) {
            throw new IllegalArgumentException("match must not be blank");
        }
        if (sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey must not be blank");
        }
        if (registryMatchCount < 0) {
            throw new IllegalArgumentException("registryMatchCount must not be negative");
        }
        if (!registryVerified && registryMatchCount != 0) {
            throw new IllegalArgumentException(
                    "unverified resources must not have registry matches"
            );
        }
    }

    @Override
    @NotNull
    public String toString() {
        return displayName;
    }
}
