package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compact production Surface result and diagnostics for PF-1.4 E. */
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
        Map<String, Long> counts = new LinkedHashMap<>();
        map.forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (surfaceClass == SurfaceClass.UNKNOWN) {
                String code = lookup.code(blockId);
                counts.merge(code, 1L, Long::sum);
            }
        });
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new BlockCodeCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Set<String> distinctSurfaceBlockCodes(int limit) {
        if (limit <= 0) {
            return Set.of();
        }
        SurfaceRegistryLookup lookup = new SurfaceRegistryLookup(registry);
        Set<String> codes = new TreeSet<>();
        map.forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            if (codes.size() < limit) {
                codes.add(lookup.code(blockId));
            }
        });
        return Set.copyOf(codes);
    }

    public record BlockCodeCount(String code, long count) {
    }

    private static final class Counter {
        private long value;
    }
}
