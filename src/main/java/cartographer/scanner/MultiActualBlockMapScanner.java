package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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
        Objects.requireNonNull(matches, "matches are required");

        List<ActualBlockMatchSpec> specs =
                new ArrayList<>(matches.size());
        List<String> normalizedMatches =
                new ArrayList<>(matches.size());

        for (String match : matches) {
            if (match == null || match.isBlank()) {
                throw new IllegalArgumentException(
                        "matches must not contain blanks"
                );
            }

            String normalized =
                    match.trim().toLowerCase(Locale.ROOT);

            if (normalizedMatches.contains(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate actual ore match: " + match
                );
            }

            normalizedMatches.add(normalized);
            specs.add(
                    new ActualBlockMatchSpec(
                            normalized,
                            ActualBlockMatchMode.ORE_CODE
                    )
            );
        }

        StreamingSession session = begin(
                blockRegistry,
                centerWorldX,
                centerWorldZ,
                radius,
                specs,
                yFilter
        );

        for (ParsedChunk chunk : chunks) {
            session.accept(chunk);
        }

        return session.finish();
    }

    public StreamingSession begin(
            Map<Integer, BlockInfo> blockRegistry,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            List<ActualBlockMatchSpec> matches,
            ActualBlockYFilter yFilter
    ) {
        Objects.requireNonNull(blockRegistry, "blockRegistry is required");
        Objects.requireNonNull(matches, "matches are required");
        Objects.requireNonNull(yFilter, "yFilter is required");

        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }

        List<ActualBlockMatchSpec> specs = matches.stream()
                .map(match -> Objects.requireNonNull(
                        match,
                        "matches must not contain null"
                ))
                .toList();

        List<MutableMap> maps = specs.stream()
                .map(spec -> new MutableMap(
                        spec.match(),
                        centerWorldX,
                        centerWorldZ,
                        radius,
                        yFilter
                ))
                .toList();

        Map<Integer, int[]> blockMatches = new HashMap<>();
        int[] wantedBlockIds = new int[blockRegistry.size()];
        int wantedSize = 0;

        for (BlockInfo block : blockRegistry.values()) {
            if (block == null || block.code() == null) {
                continue;
            }

            int[] resourceIndexes = new int[specs.size()];
            int resourceSize = 0;
            for (int index = 0; index < specs.size(); index++) {
                if (matches(specs.get(index), block.code())) {
                    resourceIndexes[resourceSize++] = index;
                }
            }

            if (resourceSize == 0) {
                continue;
            }

            int[] existingIndexes = blockMatches.get(block.id());
            int[] combinedIndexes = existingIndexes == null
                    ? Arrays.copyOf(resourceIndexes, resourceSize)
                    : appendUniqueIndexes(
                            existingIndexes,
                            resourceIndexes,
                            resourceSize
                    );
            blockMatches.put(block.id(), combinedIndexes);
            if (!contains(wantedBlockIds, wantedSize, block.id())) {
                wantedBlockIds[wantedSize++] = block.id();
            }
        }

        return new StreamingSession(
                maps,
                blockMatches,
                Arrays.copyOf(wantedBlockIds, wantedSize)
        );
    }

    private boolean matches(
            ActualBlockMatchSpec spec,
            String code
    ) {
        String normalizedCode = code.toLowerCase(Locale.ROOT);
        String normalizedMatch = spec.match().trim().toLowerCase(Locale.ROOT);

        return spec.mode() == ActualBlockMatchMode.ORE_CODE
                ? OreCodeMatcher.matchesOreCode(normalizedCode, normalizedMatch)
                : normalizedCode.contains(normalizedMatch);
    }

    private int[] appendUniqueIndexes(
            int[] existing,
            int[] additions,
            int additionSize
    ) {
        int[] combined = Arrays.copyOf(
                existing,
                existing.length + additionSize
        );
        int size = existing.length;
        for (int index = 0; index < additionSize; index++) {
            if (!contains(combined, size, additions[index])) {
                combined[size++] = additions[index];
            }
        }
        return Arrays.copyOf(combined, size);
    }

    private boolean contains(int[] values, int size, int wanted) {
        for (int index = 0; index < size; index++) {
            if (values[index] == wanted) {
                return true;
            }
        }
        return false;
    }

    public static final class StreamingSession {
        private final List<MutableMap> maps;
        private final Map<Integer, int[]> blockMatches;
        private final int[] wantedBlockIds;

        private StreamingSession(
                List<MutableMap> maps,
                Map<Integer, int[]> blockMatches,
                int[] wantedBlockIds
        ) {
            this.maps = maps;
            this.blockMatches = blockMatches;
            this.wantedBlockIds = wantedBlockIds;
        }

        public int[] wantedBlockIds() {
            return Arrays.copyOf(wantedBlockIds, wantedBlockIds.length);
        }

        public void accept(ParsedChunk chunk) {
            Objects.requireNonNull(chunk, "chunk is required");

            int baseX = chunk.coordinate().x() * ChunkCoordinate.SIZE_BLOCKS;
            int baseZ = chunk.coordinate().z() * ChunkCoordinate.SIZE_BLOCKS;
            int radius = maps.isEmpty() ? 0 : maps.getFirst().radius;
            int centerX = maps.isEmpty() ? 0 : maps.getFirst().centerX;
            int centerZ = maps.isEmpty() ? 0 : maps.getFirst().centerZ;
            ActualBlockYFilter yFilter = maps.isEmpty()
                    ? ActualBlockYFilter.unbounded()
                    : maps.getFirst().yFilter;
            long radiusSquared = (long) radius * radius;

            for (int localY = 0; localY < chunk.sizeY(); localY++) {
                int worldY = chunk.minY() + localY;
                if (!yFilter.includes(worldY)) {
                    continue;
                }

                for (int localZ = 0; localZ < chunk.sizeZ(); localZ++) {
                    int worldZ = baseZ + localZ;
                    long dz = (long) worldZ - centerZ;
                    long dzSquared = dz * dz;
                    if (dzSquared > radiusSquared) {
                        continue;
                    }

                    for (int localX = 0; localX < chunk.sizeX(); localX++) {
                        int worldX = baseX + localX;
                        long dx = (long) worldX - centerX;
                        if (dx * dx + dzSquared > radiusSquared) {
                            continue;
                        }

                        int[] resourceIndexes = blockMatches.get(
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

        /**
         * Feeds one compact PF-2.5 occurrence column without materializing a
         * ParsedChunk. Semantics match accept(ParsedChunk): circle and Y
         * filtering are applied before the same mutable maps are updated.
         */
        public void acceptIndexedOccurrence(
                ChunkPosition position,
                int blockId,
                int localX,
                int localZ,
                long localYMask
        ) {
            Objects.requireNonNull(position, "position is required");
            if (position.dimension() != 0) {
                throw new IllegalArgumentException(
                        "indexed ore occurrences require main-world dimension 0"
                );
            }
            if (localX < 0 || localX >= ChunkCoordinate.SIZE_BLOCKS
                    || localZ < 0 || localZ >= ChunkCoordinate.SIZE_BLOCKS) {
                throw new IllegalArgumentException(
                        "indexed ore local X/Z must be within 0..31"
                );
            }
            if (localYMask == 0L
                    || (localYMask & ~0xffff_ffffL) != 0L) {
                throw new IllegalArgumentException(
                        "indexed ore Y mask must contain bits only within 0..31"
                );
            }

            int[] resourceIndexes = blockMatches.get(blockId);
            if (resourceIndexes == null || resourceIndexes.length == 0) {
                return;
            }
            int baseX = Math.multiplyExact(
                    position.x(),
                    ChunkCoordinate.SIZE_BLOCKS
            );
            int baseZ = Math.multiplyExact(
                    position.z(),
                    ChunkCoordinate.SIZE_BLOCKS
            );
            int worldX = Math.addExact(baseX, localX);
            int worldZ = Math.addExact(baseZ, localZ);
            int radius = maps.isEmpty() ? 0 : maps.getFirst().radius;
            int centerX = maps.isEmpty() ? 0 : maps.getFirst().centerX;
            int centerZ = maps.isEmpty() ? 0 : maps.getFirst().centerZ;
            long dx = (long) worldX - centerX;
            long dz = (long) worldZ - centerZ;
            if (dx * dx + dz * dz > (long) radius * radius) {
                return;
            }
            ActualBlockYFilter yFilter = maps.isEmpty()
                    ? ActualBlockYFilter.unbounded()
                    : maps.getFirst().yFilter;
            int chunkMinY = Math.multiplyExact(
                    position.y(),
                    ChunkCoordinate.SIZE_BLOCKS
            );
            long remaining = localYMask;
            while (remaining != 0L) {
                int localY = Long.numberOfTrailingZeros(remaining);
                int worldY = Math.addExact(chunkMinY, localY);
                if (yFilter.includes(worldY)) {
                    for (int resourceIndex : resourceIndexes) {
                        maps.get(resourceIndex).add(
                                worldX,
                                worldZ,
                                worldY
                        );
                    }
                }
                remaining &= remaining - 1L;
            }
        }

        public List<ActualBlockMap> finish() {
            return maps.stream().map(MutableMap::toImmutable).toList();
        }
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
