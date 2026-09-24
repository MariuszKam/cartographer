package cartographer.resource;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Pure in-memory visibility policy for observed surface-object resources. */
public final class SurfaceObjectFilter {
    private SurfaceObjectFilter() {
    }

    public static List<ObservedSurfaceResource> visibleResources(
            Collection<ObservedSurfaceResource> resources,
            String searchText,
            Set<SurfaceObjectFamily> enabledFamilies
    ) {
        Objects.requireNonNull(resources, "resources are required");
        Objects.requireNonNull(enabledFamilies, "enabled families are required");
        String query = normalize(searchText);
        return resources.stream()
                .filter(Objects::nonNull)
                .filter(resource -> matchesNormalized(resource, query, enabledFamilies))
                .toList();
    }

    private static boolean matchesNormalized(
            ObservedSurfaceResource resource,
            String query,
            Set<SurfaceObjectFamily> enabledFamilies
    ) {
        boolean textMatches = query.isBlank()
                || normalize(SurfaceObjectPresentation.displayName(resource.candidate())).contains(query)
                || normalize(SurfaceObjectPresentation.qualifiedResourceKey(resource.candidate())).contains(query)
                || normalize(SurfaceObjectPresentation.familyLabels(resource.candidate().families())).contains(query);
        boolean familyMatches = enabledFamilies.isEmpty()
                || resource.candidate().families().stream().anyMatch(enabledFamilies::contains);
        return textMatches && familyMatches;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
