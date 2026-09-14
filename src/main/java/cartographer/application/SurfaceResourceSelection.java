package cartographer.application;

import cartographer.resource.ObservedSurfaceResource;

import java.util.Objects;
import java.util.Optional;

/** Explicitly selects either the legacy matcher or precomputed observations. */
public record SurfaceResourceSelection(
        Optional<SurfaceResourceMatch> legacyMatch,
        Optional<ObservedSurfaceResource> observedResource
) {
    public SurfaceResourceSelection {
        Objects.requireNonNull(legacyMatch, "legacy match is required");
        Objects.requireNonNull(observedResource, "observed resource is required");
        if (legacyMatch.isPresent() == observedResource.isPresent()) {
            throw new IllegalArgumentException(
                    "exactly one surface resource selection is required"
            );
        }
    }

    public static SurfaceResourceSelection legacy(SurfaceResourceMatch match) {
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
        if (legacyMatch.isPresent()) {
            return legacyMatch.orElseThrow().displayName();
        }
        var candidate = observedResource.orElseThrow().candidate();
        return "game".equals(candidate.namespace()) || candidate.namespace().isBlank()
                ? candidate.displayName()
                : candidate.displayName() + " [" + candidate.namespace() + "]";
    }
}
