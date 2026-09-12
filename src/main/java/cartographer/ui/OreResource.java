package cartographer.ui;

import java.util.Objects;

public record OreResource(
        String displayName,
        String match,
        String sourceKey
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
    }

    @Override
    public String toString() {
        return displayName;
    }
}
