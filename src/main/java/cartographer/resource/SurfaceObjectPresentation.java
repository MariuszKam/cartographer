package cartographer.resource;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

/** Pure presentation policy for discovered surface-object identities. */
public final class SurfaceObjectPresentation {
    private SurfaceObjectPresentation() {
    }

    public static String displayName(SurfaceObjectCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate is required");
        String name = candidate.displayName();
        String namespace = candidate.namespace();
        return namespace.isBlank() || "game".equalsIgnoreCase(namespace)
                ? name
                : name + " [" + namespace + "]";
    }

    public static String qualifiedResourceKey(SurfaceObjectCandidate candidate) {
        return Objects.requireNonNull(candidate, "candidate is required")
                .qualifiedResourceKey();
    }

    public static String familyLabel(SurfaceObjectFamily family) {
        return switch (Objects.requireNonNull(family, "family is required")) {
            case ORE_BITS -> "Ore bits";
            case FLINT -> "Flint";
            case LOOSE_STONE -> "Loose stone";
            case LOOSE_BOULDER -> "Loose boulder";
        };
    }

    public static String familyLabels(Collection<SurfaceObjectFamily> families) {
        Objects.requireNonNull(families, "families are required");
        return families.stream()
                .sorted(Comparator.naturalOrder())
                .map(SurfaceObjectPresentation::familyLabel)
                .collect(Collectors.joining(", "));
    }

    public static String familyMetricLabel(Collection<SurfaceObjectFamily> families) {
        return families.size() == 1 ? "Family" : "Families";
    }

    public static String dropdownLabel(ObservedSurfaceResource resource) {
        Objects.requireNonNull(resource, "resource is required");
        return displayName(resource.candidate()) + " — "
                + familyLabels(resource.candidate().families()) + " — "
                + resource.observedCount() + " found";
    }

    public static String statusText(ObservedSurfaceResource resource) {
        Objects.requireNonNull(resource, "resource is required");
        return "Observed: " + resource.observedCount() + " occurrences | "
                + familyMetricLabel(resource.candidate().families()) + ": "
                + familyLabels(resource.candidate().families()) + " | Registry variants: "
                + resource.candidate().blockIds().size();
    }

    public static String analysisFamilyText(SurfaceObjectAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis is required");
        return familyLabels(analysis.families());
    }
}
