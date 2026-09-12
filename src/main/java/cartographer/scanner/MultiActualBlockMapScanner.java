package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MultiActualBlockMapScanner {

    public List<ActualBlockMap> scan(
            List<ParsedChunk> chunks,
            Map<Integer, BlockInfo> blockRegistry,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            List<String> matches,
            ActualBlockYFilter yFilter
    ) {
        Objects.requireNonNull(chunks, "chunks are required");
        Objects.requireNonNull(blockRegistry, "blockRegistry is required");
        Objects.requireNonNull(matches, "matches are required");
        Objects.requireNonNull(yFilter, "yFilter is required");

        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }

        Map<String, Integer> matchIndexes = new LinkedHashMap<>();
        List<String> normalizedMatches = new ArrayList<>();
        for (String match : matches) {
            if (match == null || match.isBlank()) {
                throw new IllegalArgumentException("matches must not contain blanks");
            }
            String normalized = match.trim().toLowerCase(Locale.ROOT);
            if (matchIndexes.putIfAbsent(normalized, normalizedMatches.size()) != null) {
                throw new IllegalArgumentException("Duplicate actual ore match: " + match);
            }
            normalizedMatches.add(normalized);
        }

        List<MutableMap> maps = normalizedMatches.stream()
                .map(match -> new MutableMap(
                        match,
                        centerWorldX,
                        centerWorldZ,
                        radius,
                        yFilter
                ))
                .toList();

        Map<Integer, List<Integer>> blockMatches = new HashMap<>();
        for (BlockInfo block : blockRegistry.values()) {
            if (block == null || block.code() == null) {
                continue;
            }
            String code = block.code().toLowerCase(Locale.ROOT);
            for (int index = 0; index < normalizedMatches.size(); index++) {
                if (OreCodeMatcher.matchesOreCode(
                        code,
                        normalizedMatches.get(index)
                )) {
                    blockMatches.computeIfAbsent(block.id(), ignored -> new ArrayList<>())
                            .add(index);
                }
            }
        }

        long radiusSquared = (long) radius * radius;
        for (ParsedChunk chunk : chunks) {
            Objects.requireNonNull(chunk, "chunks must not contain null");
            int baseX = chunk.coordinate().x() * ChunkCoordinate.SIZE_BLOCKS;
            int baseZ = chunk.coordinate().z() * ChunkCoordinate.SIZE_BLOCKS;
            for (int localY = 0; localY < chunk.sizeY(); localY++) {
                int worldY = chunk.minY() + localY;
                if (!yFilter.includes(worldY)) {
                    continue;
                }
                for (int localZ = 0; localZ < chunk.sizeZ(); localZ++) {
                    int worldZ = baseZ + localZ;
                    long dz = (long) worldZ - centerWorldZ;
                    long dzSquared = dz * dz;
                    if (dzSquared > radiusSquared) {
                        continue;
                    }
                    for (int localX = 0; localX < chunk.sizeX(); localX++) {
                        int worldX = baseX + localX;
                        long dx = (long) worldX - centerWorldX;
                        if (dx * dx + dzSquared > radiusSquared) {
                            continue;
                        }
                        List<Integer> resourceIndexes = blockMatches.get(
                                chunk.blockIdAt(localX, localY, localZ)
                        );
                        if (resourceIndexes == null) {
                            continue;
                        }
                        for (int resourceIndex : resourceIndexes) {
                            maps.get(resourceIndex).add(worldX, worldZ, worldY);
                        }
                    }
                }
            }
        }

        return maps.stream().map(MutableMap::toImmutable).toList();
    }

    private static final class MutableMap {
        private final String match;
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final ActualBlockYFilter yFilter;
        private final Map<Long, MutableCell> cells = new HashMap<>();
        private long matchingBlocks;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private MutableMap(
                String match,
                int centerX,
                int centerZ,
                int radius,
                ActualBlockYFilter yFilter
        ) {
            this.match = match;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.yFilter = yFilter;
        }

        private void add(int worldX, int worldZ, int worldY) {
            matchingBlocks++;
            minY = Math.min(minY, worldY);
            maxY = Math.max(maxY, worldY);
            long key = (((long) worldX) << 32) ^ (worldZ & 0xffffffffL);
            cells.computeIfAbsent(key, ignored -> new MutableCell(worldX, worldZ))
                    .add(worldY);
        }

        private ActualBlockMap toImmutable() {
            List<ActualBlockMapCell> result = cells.values().stream()
                    .map(MutableCell::toImmutable)
                    .sorted(Comparator.comparingInt(ActualBlockMapCell::worldZ)
                            .thenComparingInt(ActualBlockMapCell::worldX))
                    .toList();
            return new ActualBlockMap(
                    match,
                    centerX,
                    centerZ,
                    radius,
                    yFilter,
                    matchingBlocks,
                    result.isEmpty() ? -1 : minY,
                    result.isEmpty() ? -1 : maxY,
                    result
            );
        }

    }

    private static final class MutableCell {
        private final int worldX;
        private final int worldZ;
        private int count;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private MutableCell(int worldX, int worldZ) {
            this.worldX = worldX;
            this.worldZ = worldZ;
        }

        private void add(int worldY) {
            count++;
            minY = Math.min(minY, worldY);
            maxY = Math.max(maxY, worldY);
        }

        private ActualBlockMapCell toImmutable() {
            return new ActualBlockMapCell(worldX, worldZ, count, minY, maxY);
        }
    }
}
