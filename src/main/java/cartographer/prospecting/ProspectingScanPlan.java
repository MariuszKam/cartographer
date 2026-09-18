package cartographer.prospecting;

import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;

import java.util.List;
import java.util.Objects;

/** Immutable operation plan shared by all fused prospecting consumers. */
public record ProspectingScanPlan(
        List<String> resourceKeys,
        List<ChunkPosition> positions,
        WorldMetadata metadata,
        int minY,
        int maxYExclusive,
        int[] interestingBlockIds
) {
    public ProspectingScanPlan {
        resourceKeys = List.copyOf(Objects.requireNonNull(resourceKeys, "resource keys are required"));
        positions = List.copyOf(Objects.requireNonNull(positions, "positions are required"));
        Objects.requireNonNull(metadata, "metadata is required");
        interestingBlockIds = Objects.requireNonNull(interestingBlockIds, "interesting block ids are required").clone();
        if (minY < 0 || maxYExclusive <= minY || maxYExclusive > metadata.mapSizeY()) {
            throw new IllegalArgumentException("invalid prospecting Y range");
        }
    }

    @Override
    public int[] interestingBlockIds() { return interestingBlockIds.clone(); }
}
