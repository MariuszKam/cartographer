package cartographer.resource;

import java.util.List;

/** Exact physical loose-object occurrences; no clustering or deposit semantics. */
public record SurfaceObjectAnalysis(
        String displayName,
        String qualifiedResourceKey,
        int registryVariantCount,
        List<SurfaceResourcePoint> occurrences
) implements SurfaceRenderAnalysis {
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
    }

    public int occurrenceCount() { return occurrences.size(); }
}
