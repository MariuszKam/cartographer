package cartographer.save;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable save-level information loaded once when a SaveSession opens. */
public record SaveSnapshot(
        WorldMetadata metadata,
        Map<Integer, BlockInfo> blockRegistry
) {
    public SaveSnapshot {
        metadata = Objects.requireNonNull(metadata, "world metadata is required");
        Map<Integer, BlockInfo> copied = new LinkedHashMap<>(
                Objects.requireNonNull(blockRegistry, "block registry is required")
        );
        copied.replaceAll((id, block) -> Objects.requireNonNull(block, "block registry cannot contain null values"));
        blockRegistry = Map.copyOf(copied);
    }
}
