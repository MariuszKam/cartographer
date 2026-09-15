package cartographer.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable aggregate analysis for the selected logical surface objects. */
public record SurfaceObjectSelectionAnalysis(List<SurfaceObjectAnalysis> resources)
        implements SurfaceRenderAnalysis {
    public SurfaceObjectSelectionAnalysis {
        Objects.requireNonNull(resources, "resources are required");
        List<SurfaceObjectAnalysis> sorted = new ArrayList<>(resources);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("resources must not contain null");
        }
        sorted.sort(Comparator.comparing(SurfaceObjectAnalysis::qualifiedResourceKey));
        if (sorted.isEmpty() || new HashSet<>(sorted.stream()
                .map(SurfaceObjectAnalysis::qualifiedResourceKey).toList()).size() != sorted.size()) {
            throw new IllegalArgumentException("object analysis resources must have unique keys");
        }
        resources = List.copyOf(sorted);
    }

    public int resourceCount() {
        return resources.size();
    }

    public int occurrenceCount() {
        return resources.stream().mapToInt(SurfaceObjectAnalysis::occurrenceCount).sum();
    }
}
