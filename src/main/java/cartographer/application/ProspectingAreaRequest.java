package cartographer.application;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProspectingAreaRequest(
        Path savePath,
        Optional<WorldPosition> center,
        int radius,
        List<String> resources
) {
    public ProspectingAreaRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(center, "center is required");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources are required"));
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (resources.stream().anyMatch(resource -> resource == null || resource.isBlank())) {
            throw new IllegalArgumentException("resources must not contain blank values");
        }
        resources = resources.stream()
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** Compatibility constructor: empty means all resources, present means one filter term. */
    public ProspectingAreaRequest(
            Path savePath,
            Optional<WorldPosition> center,
            int radius,
            Optional<String> resource
    ) {
        this(
                savePath,
                center,
                radius,
                Objects.requireNonNull(resource, "resource is required")
                        .map(List::of)
                        .orElseGet(List::of)
        );
    }

    /** Compatibility view for callers that still display the single-resource form. */
    public Optional<String> resource() {
        return resources.size() == 1
                ? Optional.of(resources.getFirst())
                : Optional.empty();
    }

    public boolean allResources() {
        return resources.isEmpty();
    }
}
