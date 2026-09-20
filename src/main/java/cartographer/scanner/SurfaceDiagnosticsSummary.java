package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lightweight Surface diagnostics that do not retain request-shaped cell data.
 *
 * <p>The summary preserves the reporting contract needed by Map/Ore consumers
 * while exact {@link SurfaceMapScanResult} state remains available only to
 * analysis consumers that genuinely inspect world columns.</p>
 */
public final class SurfaceDiagnosticsSummary {
    private final int chunksScanned;
    private final int columnsScanned;
    private final int emptyColumns;
    private final int liquidUnavailableColumns;
    private final long waterColumns;
    private final long unknownSurfaceBlocks;
    private final Map<String, Long> unknownCodeCounts;
    private final List<String> distinctCodesInEncounterOrder;

    private SurfaceDiagnosticsSummary(
            int chunksScanned,
            int columnsScanned,
            int emptyColumns,
            int liquidUnavailableColumns,
            long waterColumns,
            long unknownSurfaceBlocks,
            Map<String, Long> unknownCodeCounts,
            List<String> distinctCodesInEncounterOrder
    ) {
        if (chunksScanned < 0
                || columnsScanned < 0
                || emptyColumns < 0
                || liquidUnavailableColumns < 0
                || waterColumns < 0
                || unknownSurfaceBlocks < 0) {
            throw new IllegalArgumentException(
                    "Surface diagnostic counters cannot be negative"
            );
        }
        this.chunksScanned = chunksScanned;
        this.columnsScanned = columnsScanned;
        this.emptyColumns = emptyColumns;
        this.liquidUnavailableColumns = liquidUnavailableColumns;
        this.waterColumns = waterColumns;
        this.unknownSurfaceBlocks = unknownSurfaceBlocks;
        this.unknownCodeCounts = Map.copyOf(Objects.requireNonNull(
                unknownCodeCounts,
                "unknown code counts are required"
        ));
        this.distinctCodesInEncounterOrder = List.copyOf(
                Objects.requireNonNull(
                        distinctCodesInEncounterOrder,
                        "distinct codes are required"
                )
        );
    }

    public static Builder builder(Map<Integer, BlockInfo> registry) {
        return new Builder(new SurfaceRegistryLookup(registry));
    }

    public static SurfaceDiagnosticsSummary empty() {
        return new SurfaceDiagnosticsSummary(
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                List.of()
        );
    }

    public static SurfaceDiagnosticsSummary from(
            SurfaceMapScanResult result
    ) {
        Objects.requireNonNull(result, "Surface result is required");
        Builder builder = builder(result.registry())
                .counters(
                        result.chunksScanned(),
                        result.columnsScanned(),
                        result.emptyColumns(),
                        result.liquidUnavailableColumns()
                );
        result.map().forEachResolvedCell(
                (x, z, y, blockId, liquidId, surfaceClass) ->
                        builder.acceptResolved(blockId, surfaceClass)
        );
        return builder.build();
    }

    public int chunksScanned() {
        return chunksScanned;
    }

    public int columnsScanned() {
        return columnsScanned;
    }

    public int emptyColumns() {
        return emptyColumns;
    }

    public int liquidUnavailableColumns() {
        return liquidUnavailableColumns;
    }

    public long waterColumns() {
        return waterColumns;
    }

    public long unknownSurfaceBlocks() {
        return unknownSurfaceBlocks;
    }

    public List<BlockCodeCount> topUnknownSurfaceBlockCodes(int limit) {
        if (limit <= 0 || unknownCodeCounts.isEmpty()) {
            return List.of();
        }
        return unknownCodeCounts.entrySet().stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry.comparingByKey())
                )
                .limit(limit)
                .map(entry -> new BlockCodeCount(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    public Set<String> distinctSurfaceBlockCodes(int limit) {
        if (limit <= 0 || distinctCodesInEncounterOrder.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new TreeSet<>();
        for (String code : distinctCodesInEncounterOrder) {
            codes.add(code);
            if (codes.size() >= limit) {
                break;
            }
        }
        return Set.copyOf(codes);
    }

    public record BlockCodeCount(String code, long count) {
        public BlockCodeCount {
            Objects.requireNonNull(code, "block code is required");
            if (count < 0) {
                throw new IllegalArgumentException(
                        "block code count cannot be negative"
                );
            }
        }
    }

    public static final class Builder {
        private final SurfaceRegistryLookup lookup;
        private final Map<String, Long> unknownCodeCounts =
                new LinkedHashMap<>();
        private final LinkedHashSet<String> distinctCodes =
                new LinkedHashSet<>();
        private int chunksScanned;
        private int columnsScanned;
        private int emptyColumns;
        private int liquidUnavailableColumns;
        private long waterColumns;
        private long unknownSurfaceBlocks;

        private Builder(SurfaceRegistryLookup lookup) {
            this.lookup = Objects.requireNonNull(
                    lookup,
                    "Surface registry lookup is required"
            );
        }

        public Builder counters(
                int chunksScanned,
                int columnsScanned,
                int emptyColumns,
                int liquidUnavailableColumns
        ) {
            if (chunksScanned < 0
                    || columnsScanned < 0
                    || emptyColumns < 0
                    || liquidUnavailableColumns < 0) {
                throw new IllegalArgumentException(
                        "Surface diagnostic counters cannot be negative"
                );
            }
            this.chunksScanned = chunksScanned;
            this.columnsScanned = columnsScanned;
            this.emptyColumns = emptyColumns;
            this.liquidUnavailableColumns = liquidUnavailableColumns;
            return this;
        }

        public Builder addColumnsScanned(int count) {
            columnsScanned = Math.addExact(
                    columnsScanned,
                    nonNegative(count, "columns scanned")
            );
            return this;
        }

        public Builder addEmptyColumns(int count) {
            emptyColumns = Math.addExact(
                    emptyColumns,
                    nonNegative(count, "empty columns")
            );
            return this;
        }

        public Builder addLiquidUnavailableColumns(int count) {
            liquidUnavailableColumns = Math.addExact(
                    liquidUnavailableColumns,
                    nonNegative(count, "liquid unavailable columns")
            );
            return this;
        }

        public void acceptResolved(
                int blockId,
                SurfaceClass surfaceClass
        ) {
            Objects.requireNonNull(
                    surfaceClass,
                    "surface class is required"
            );
            int slot = lookup.slot(blockId);
            String code = slot < 0
                    ? "unknown:" + blockId
                    : lookup.codeAt(slot);
            distinctCodes.add(code);

            if (surfaceClass == SurfaceClass.WATER) {
                waterColumns = Math.addExact(waterColumns, 1L);
            }
            if (surfaceClass == SurfaceClass.UNKNOWN) {
                unknownSurfaceBlocks = Math.addExact(
                        unknownSurfaceBlocks,
                        1L
                );
                unknownCodeCounts.merge(code, 1L, Math::addExact);
            }
        }

        public SurfaceDiagnosticsSummary build() {
            return new SurfaceDiagnosticsSummary(
                    chunksScanned,
                    columnsScanned,
                    emptyColumns,
                    liquidUnavailableColumns,
                    waterColumns,
                    unknownSurfaceBlocks,
                    unknownCodeCounts,
                    new ArrayList<>(distinctCodes)
            );
        }

        private static int nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        name + " cannot be negative"
                );
            }
            return value;
        }
    }
}
