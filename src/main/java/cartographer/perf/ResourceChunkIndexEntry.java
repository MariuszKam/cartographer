package cartographer.perf;

import cartographer.model.ChunkPosition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One terminal PF-2.5 resource-index result for a requested source chunk.
 */
public record ResourceChunkIndexEntry(
        ChunkPosition position,
        ResourceChunkCoverageStatus coverageStatus,
        List<ResourceOccurrence> occurrences
) {
    public ResourceChunkIndexEntry {
        Objects.requireNonNull(position, "position is required");
        Objects.requireNonNull(coverageStatus, "coverageStatus is required");
        if (position.dimension() != 0) {
            throw new IllegalArgumentException(
                    "resource index entries only support main-world dimension 0"
            );
        }
        occurrences = List.copyOf(
                Objects.requireNonNull(occurrences, "occurrences are required")
        );
        for (ResourceOccurrence occurrence : occurrences) {
            Objects.requireNonNull(
                    occurrence,
                    "occurrences cannot contain null"
            );
            if (!position.equals(occurrence.position())) {
                throw new IllegalArgumentException(
                        "occurrence position must match resource index entry"
                );
            }
        }
        if (coverageStatus != ResourceChunkCoverageStatus.AVAILABLE
                && !occurrences.isEmpty()) {
            throw new IllegalArgumentException(
                    "missing/failed resource chunks cannot contain occurrences"
            );
        }
        List<ResourceOccurrence> ordered = occurrences.stream()
                .sorted(
                        Comparator.comparingInt(ResourceOccurrence::blockId)
                                .thenComparingInt(ResourceOccurrence::localZ)
                                .thenComparingInt(ResourceOccurrence::localX)
                )
                .toList();
        if (!ordered.equals(occurrences)) {
            occurrences = ordered;
        }
        for (int index = 1; index < occurrences.size(); index++) {
            ResourceOccurrence previous = occurrences.get(index - 1);
            ResourceOccurrence current = occurrences.get(index);
            if (previous.blockId() == current.blockId()
                    && previous.localX() == current.localX()
                    && previous.localZ() == current.localZ()) {
                throw new IllegalArgumentException(
                        "duplicate resource occurrence column"
                );
            }
        }
    }

    public static ResourceChunkIndexEntry available(
            ChunkPosition position,
            List<ResourceOccurrence> occurrences
    ) {
        return new ResourceChunkIndexEntry(
                position,
                ResourceChunkCoverageStatus.AVAILABLE,
                occurrences
        );
    }

    public static ResourceChunkIndexEntry missing(ChunkPosition position) {
        return new ResourceChunkIndexEntry(
                position,
                ResourceChunkCoverageStatus.MISSING,
                List.of()
        );
    }

    public static ResourceChunkIndexEntry failed(ChunkPosition position) {
        return new ResourceChunkIndexEntry(
                position,
                ResourceChunkCoverageStatus.FAILED,
                List.of()
        );
    }

    public List<Integer> blockIdsPresent() {
        return occurrences.stream()
                .map(ResourceOccurrence::blockId)
                .distinct()
                .toList();
    }
}
