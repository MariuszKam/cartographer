package cartographer.application;

import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.SurfaceObjectPresentation;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Explicitly selects either a surface material or precomputed observations. */
public record SurfaceResourceSelection(
        Optional<SurfaceMaterialMatch> material,
        List<ObservedSurfaceResource> observedResources
) {
    public SurfaceResourceSelection {
        Objects.requireNonNull(material, "material is required");
        Objects.requireNonNull(observedResources, "observed resources are required");
        List<ObservedSurfaceResource> sorted = new ArrayList<>(observedResources);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("observed resources must not contain null");
        }
        sorted.sort(Comparator.comparing(resource ->
                resource.candidate().qualifiedResourceKey()));
        if (new HashSet<>(sorted.stream()
                .map(resource -> resource.candidate().qualifiedResourceKey()).toList()).size()
                != sorted.size()) {
            throw new IllegalArgumentException("observed resources must have unique qualified keys");
        }
        observedResources = List.copyOf(sorted);
        if (material.isPresent() != observedResources.isEmpty()) {
            throw new IllegalArgumentException(
                    "exactly one surface resource selection is required"
            );
        }
    }

    public static SurfaceResourceSelection material(SurfaceMaterialMatch match) {
        return new SurfaceResourceSelection(
                Optional.of(Objects.requireNonNull(match, "match is required")),
                List.of()
        );
    }

    public static SurfaceResourceSelection observedResources(
            List<ObservedSurfaceResource> resources
    ) {
        return new SurfaceResourceSelection(
                Optional.empty(),
                Objects.requireNonNull(resources, "resources are required")
        );
    }

    public String displayName() {
        if (material.isPresent()) {
            return material.orElseThrow().displayName();
        }
        return observedResources.size() == 1
                ? SurfaceObjectPresentation.displayName(observedResources.getFirst().candidate())
                : "Surface objects";
    }
}
