package cartographer.perf;

import cartographer.model.BlockInfo;
import cartographer.scanner.OreCodeMatcher;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry-derived catalog of real ore block IDs eligible for PF-2.5 indexing.
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
        List<BlockInfo> blocks = registry.values().stream()
                .filter(Objects::nonNull)
                .filter(block -> block.code() != null)
                .filter(block -> OreCodeMatcher.isOreCode(block.code()))
                .sorted(
                        java.util.Comparator.comparingInt(BlockInfo::id)
                                .thenComparing(BlockInfo::code)
                )
                .toList();
        return new ResourceBlockCatalog(blocks);
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
