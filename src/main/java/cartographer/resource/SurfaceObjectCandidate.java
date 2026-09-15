package cartographer.resource;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** A logical surface-object candidate derived from registry data, not world observations. */
public record SurfaceObjectCandidate(
        String namespace,
        String resourceKey,
        String displayName,
        SortedSet<SurfaceObjectFamily> families,
        SortedSet<Integer> blockIds
) {
    public SurfaceObjectCandidate {
        if (namespace == null || namespace.contains(":")) {
            throw new IllegalArgumentException("Candidate namespace is invalid");
        }
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("Candidate resource key is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Candidate display name is required");
        }
        families = immutableSortedSet(families, "families");
        blockIds = immutableSortedSet(blockIds, "block ids");
        if (families.isEmpty() || blockIds.isEmpty()) {
            throw new IllegalArgumentException("Candidate must contain families and block ids");
        }
    }

    public String qualifiedResourceKey() {
        return namespace.isBlank()
                ? resourceKey
                : namespace + ":" + resourceKey;
    }

    private static <T extends Comparable<? super T>> SortedSet<T> immutableSortedSet(
            Set<T> values,
            String label
    ) {
        return Collections.unmodifiableSortedSet(
                new TreeSet<>(Objects.requireNonNull(values, label + " are required"))
        );
    }
}
