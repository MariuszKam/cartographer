package cartographer.resource;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable filter controls state, intentionally independent from selection state. */
public record SurfaceObjectFilterState(
        String searchText,
        Set<SurfaceObjectFamily> enabledFamilies
) {
    public SurfaceObjectFilterState {
        searchText = SurfaceObjectFilter.normalize(searchText);
        Objects.requireNonNull(enabledFamilies, "enabled families are required");
        enabledFamilies = Collections.unmodifiableSet(new TreeSet<>(enabledFamilies));
    }

    public SurfaceObjectFilterState reset() {
        return new SurfaceObjectFilterState("", Set.of());
    }
}
