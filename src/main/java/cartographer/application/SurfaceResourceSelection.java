package cartographer.application;

import cartographer.resource.ObservedSurfaceResource;

import java.util.Objects;
import java.util.Optional;

/** Explicitly selects either a surface material or precomputed observations. */
public record SurfaceResourceSelection(
        Optional<SurfaceMaterialMatch> material,
        Optional<ObservedSurfaceResource> observedResource
) {
    public SurfaceResourceSelection {
        Objects.requireNonNull(material, "material is required");
        Objects.requireNonNull(observedResource, "observed resource is required");
        if (material.isPresent() == observedResource.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one surface resource selection is required"
            );
        }
    }

    public static SurfaceResourceSelection material(SurfaceMaterialMatch match) {
        return new SurfaceResourceSelection(
                Optional.of(Objects.requireNonNull(match, "match is required")),
                Optional.empty()
        );
    }

    public static SurfaceResourceSelection observed(
            ObservedSurfaceResource resource
    ) {
        return new SurfaceResourceSelection(
                Optional.empty(),
                Optional.of(Objects.requireNonNull(resource, "resource is required"))
        );
    }

    public String displayName() {
        if (material.isPresent()) {
            return material.orElseThrow().displayName();
        }
        var candidate = observedResource.orElseThrow().candidate();
        return "game".equals(candidate.namespace()) || candidate.namespace().isBlank()
                ? candidate.displayName()
                : candidate.displayName() + " [" + candidate.namespace() + "]";
    }
}
