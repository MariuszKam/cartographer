package cartographer.resource;

import java.util.Objects;
import java.util.Optional;

public record SurfaceObjectIdentity(
        String originalCode,
        String namespace,
        String normalizedPath,
        SurfaceObjectFamily family,
        String resourceKey,
        String displayName,
        Optional<String> hostRock,
        Optional<String> variant
) {
    public SurfaceObjectIdentity {
        if (originalCode == null || originalCode.isBlank()) {
            throw new IllegalArgumentException("Original block code is required");
        }
        if (namespace == null || namespace.contains(":")) {
            throw new IllegalArgumentException("Normalized namespace is invalid");
        }
        if (normalizedPath == null || normalizedPath.isBlank()) {
            throw new IllegalArgumentException("Normalized block path is required");
        }
        Objects.requireNonNull(family, "Surface object family is required");
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("Surface object resource key is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Surface object display name is required");
        }
        Objects.requireNonNull(hostRock, "Host rock is required");
        Objects.requireNonNull(variant, "Variant is required");
    }

    public String qualifiedResourceKey() {
        return namespace.isBlank()
                ? resourceKey
                : namespace + ":" + resourceKey;
    }
}
