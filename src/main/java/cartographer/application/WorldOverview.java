package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable save-level data needed when a Workstation world is opened. */
public record WorldOverview(
        WorldMetadata metadata,
        Map<Integer, BlockInfo> blockRegistry,
        Optional<WorldPosition> playerAbsolute,
        List<String> resourceKeys
) {
    public WorldOverview {
        Objects.requireNonNull(metadata, "world metadata is required");
        Map<Integer, BlockInfo> copiedRegistry = new LinkedHashMap<>(
                Objects.requireNonNull(blockRegistry, "block registry is required")
        );
        copiedRegistry.replaceAll((id, block) ->
                Objects.requireNonNull(block, "block registry cannot contain null values"));
        blockRegistry = Map.copyOf(copiedRegistry);
        Objects.requireNonNull(
                playerAbsolute,
                "player position option is required"
        );
        resourceKeys = List.copyOf(
                Objects.requireNonNull(resourceKeys, "resource keys are required")
        );
    }
}
