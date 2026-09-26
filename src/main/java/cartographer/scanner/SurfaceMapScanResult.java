package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compact production Surface result and diagnostics for Surface scanning. */
public record SurfaceMapScanResult(
        SurfaceMap map,
        Map<Integer, BlockInfo> registry,
        int chunksScanned,
        int columnsScanned,
        int emptyColumns,
        int liquidUnavailableColumns
) {
    public SurfaceMapScanResult {
        Objects.requireNonNull(map, "surface map is required");
        registry = Map.copyOf(Objects.requireNonNull(registry, "registry is required"));
        if (chunksScanned < 0 || columnsScanned < 0 || emptyColumns < 0
                || liquidUnavailableColumns < 0) {
            throw new IllegalArgumentException("surface counters cannot be negative");
        }
    }

    public long waterColumns() {
        Counter counter = new Counter();
        map.forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (surfaceClass == SurfaceClass.WATER) {
                counter.value++;
            }
        });
        return counter.value;
    }

    public long unknownSurfaceBlocks() {
        Counter counter = new Counter();
        map.forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (surfaceClass == SurfaceClass.UNKNOWN) {
                counter.value++;
            }
        });
        return counter.value;
    }

    public List<BlockCodeCount> topUnknownSurfaceBlockCodes(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        SurfaceRegistryLookup lookup = new SurfaceRegistryLookup(registry);
        PrimitiveIdCounts counts = new PrimitiveIdCounts();
        map.forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (surfaceClass == SurfaceClass.UNKNOWN) {
                counts.increment(blockId);
            }
        });
        Map<String, Long> output = new LinkedHashMap<>();
        for (int index = 0; index < counts.size; index++) {
            output.merge(lookup.code(counts.ids[index]), counts.counts[index], Long::sum);
        }
        return output.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new BlockCodeCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record BlockCodeCount(String code, long count) {
    }

    private static final class Counter {
        private long value;
    }

    private static final class PrimitiveIdCounts {
        private int[] ids = new int[8];
        private long[] counts = new long[8];
        private int size;

        private void increment(int id) {
            for (int index = 0; index < size; index++) {
                if (ids[index] == id) {
                    counts[index]++;
                    return;
                }
            }
            if (size == ids.length) {
                ids = java.util.Arrays.copyOf(ids, Math.multiplyExact(size, 2));
                counts = java.util.Arrays.copyOf(counts, Math.multiplyExact(size, 2));
            }
            ids[size] = id;
            counts[size] = 1;
            size++;
        }
    }

}
