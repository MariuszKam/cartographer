package cartographer.perf;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Small immutable revision-scoped header used by PF-2.6 warm operations.
 *
 * <p>This is derived data only. The Vintage Story save remains authoritative;
 * a missing or incompatible header is a cache miss and must fall back to the
 * source path.</p>
 */
public record WorldSnapshotHeader(
        WorldMetadata metadata,
        Map<Integer, BlockInfo> blockRegistry,
        Optional<WorldPosition> player
) {
    public WorldSnapshotHeader {
        metadata = Objects.requireNonNull(metadata, "metadata is required");
        Map<Integer, BlockInfo> copy = new LinkedHashMap<>(
                Objects.requireNonNull(
                        blockRegistry,
                        "blockRegistry is required"
                )
        );
        copy.replaceAll((id, block) -> Objects.requireNonNull(
                block,
                "blockRegistry cannot contain null values"
        ));
        blockRegistry = Map.copyOf(copy);
        player = Objects.requireNonNull(player, "player is required");
    }
}
