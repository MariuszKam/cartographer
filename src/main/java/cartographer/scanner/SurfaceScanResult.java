package cartographer.scanner;

import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record SurfaceScanResult(
        List<SurfaceBlock> blocks,
        int chunksScanned,
        int columnsScanned,
        int emptyColumns,
        int liquidUnavailableColumns
) {
    public long waterColumns() {
        return blocks.stream()
                .filter(
                        block ->
                                block.surfaceClass() == SurfaceClass.WATER
                )
                .count();
    }

    public long unknownSurfaceBlocks() {
        return blocks.stream()
                .filter(
                        block ->
                                block.surfaceClass() == SurfaceClass.UNKNOWN
                )
                .count();
    }

    public List<BlockCodeCount> topUnknownSurfaceBlockCodes(
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        Map<String, Long> counts =
                new LinkedHashMap<>();

        for (SurfaceBlock block : blocks) {
            if (block.surfaceClass() != SurfaceClass.UNKNOWN) {
                continue;
            }

            counts.merge(
                    block.blockInfo()
                            .code(),
                    1L,
                    Long::sum
            );
        }

        return counts.entrySet()
                .stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue()
                                .reversed()
                                .thenComparing(
                                        Map.Entry.comparingByKey()
                                )
                )
                .limit(
                        limit
                )
                .map(
                        entry ->
                                new BlockCodeCount(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                )
                .collect(
                        Collectors.toUnmodifiableList()
                );
    }

    public Set<String> distinctSurfaceBlockCodes(
            int limit
    ) {
        Set<String> codes =
                new TreeSet<>();

        for (SurfaceBlock block : blocks) {
            codes.add(
                    block.blockInfo()
                            .code()
            );

            if (codes.size() >= limit) {
                break;
            }
        }

        return Set.copyOf(
                codes
        );
    }

    public Map<SurfaceClass, Integer> classCounts() {
        Map<SurfaceClass, Integer> counts =
                new EnumMap<>(
                        SurfaceClass.class
                );

        for (SurfaceBlock block : blocks) {
            counts.merge(
                    block.surfaceClass(),
                    1,
                    Integer::sum
            );
        }

        return Map.copyOf(
                counts
        );
    }

    public record BlockCodeCount(
            String code,
            long count
    ) {
    }
}
