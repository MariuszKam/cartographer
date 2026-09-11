package cartographer.scanner;

import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
}
