package cartographer.index;

import cartographer.model.BlockInfo;
import cartographer.scanner.OreCodeMatcher;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Registry-derived catalog of real ore block IDs eligible for resource indexing.
 *
 * <p>OreMaps are intentionally not consulted here. They may describe
 * prospecting probability/resources, but they are not evidence that an actual
 * ore block exists in a source chunk.</p>
 */
public final class ResourceBlockCatalog {
    private final List<BlockInfo> blocks;
    private final int[] blockIds;

    private ResourceBlockCatalog(List<BlockInfo> blocks) {
        this.blocks = List.copyOf(blocks);
        this.blockIds = blocks.stream()
                .mapToInt(BlockInfo::id)
                .toArray();
    }

    public static ResourceBlockCatalog from(
            Map<Integer, BlockInfo> registry
    ) {
        Objects.requireNonNull(registry, "registry is required");
        TreeMap<Integer, BlockInfo> byId = new TreeMap<>();
        for (BlockInfo block : registry.values()) {
            if (block == null
                    || block.id() < 0
                    || block.code() == null
                    || !OreCodeMatcher.isOreCode(block.code())) {
                continue;
            }
            byId.merge(
                    block.id(),
                    block,
                    (left, right) ->
                            left.code().compareTo(right.code()) <= 0
                                    ? left
                                    : right
            );
        }
        return new ResourceBlockCatalog(
                List.copyOf(byId.values())
        );
    }

    public List<BlockInfo> blocks() {
        return blocks;
    }

    public int[] blockIds() {
        return blockIds.clone();
    }

    public boolean isEmpty() {
        return blockIds.length == 0;
    }

    public boolean contains(int blockId) {
        return java.util.Arrays.binarySearch(blockIds, blockId) >= 0;
    }
}
