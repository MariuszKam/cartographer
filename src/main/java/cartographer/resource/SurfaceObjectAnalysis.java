package cartographer.resource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/** Exact physical loose-object occurrences; no clustering or deposit semantics. */
public record SurfaceObjectAnalysis(
        String displayName,
        String qualifiedResourceKey,
        int registryVariantCount,
        List<SurfaceResourcePoint> occurrences,
        SortedSet<SurfaceObjectFamily> families
) {
    public SurfaceObjectAnalysis {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Surface object display name is required");
        }
        if (qualifiedResourceKey == null || qualifiedResourceKey.isBlank()) {
            throw new IllegalArgumentException("Surface object resource key is required");
        }
        if (registryVariantCount < 0) {
            throw new IllegalArgumentException("Surface object variant count cannot be negative");
        }
        occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
        Objects.requireNonNull(families, "Surface object families are required");
        if (families.isEmpty()) {
            throw new IllegalArgumentException("Surface object families are required");
        }
        families = Collections.unmodifiableSortedSet(new TreeSet<>(families));
    }

    public int occurrenceCount() { return occurrences.size(); }
}
