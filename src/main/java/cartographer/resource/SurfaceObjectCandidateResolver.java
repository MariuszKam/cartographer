package cartographer.resource;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Resolves user resource keys without losing namespace identity. */
public final class SurfaceObjectCandidateResolver {
    public SurfaceObjectCandidate resolve(
            SurfaceObjectCandidateCatalog catalog,
            String resourceKey
    ) {
        Objects.requireNonNull(catalog, "catalog is required");
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("surface resource key is required");
        }
        String input = resourceKey.trim().toLowerCase(Locale.ROOT);
        List<SurfaceObjectCandidate> matches = catalog.candidates().stream()
                .filter(candidate -> input.contains(":")
                        ? candidate.qualifiedResourceKey().equalsIgnoreCase(input)
                        : candidate.resourceKey().equalsIgnoreCase(input))
                .sorted(Comparator.comparing(SurfaceObjectCandidate::qualifiedResourceKey))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "No observed surface resource candidate matches \""
                            + resourceKey.trim() + "\"."
            );
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous surface resource \"" + resourceKey.trim() + "\". Use one of: "
                            + String.join(", ", matches.stream()
                            .map(SurfaceObjectCandidate::qualifiedResourceKey).toList())
            );
        }
        return matches.getFirst();
    }
}
