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

    public boolean allResources() {
        return resources.isEmpty();
    }
}
