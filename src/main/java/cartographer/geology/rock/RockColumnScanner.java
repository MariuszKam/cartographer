package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RockColumnScanner {

    public RockMap scan(
            Collection<ParsedChunk> chunks,
            RockCatalog catalog,
            WorldPosition center,
            int radius,
            int minWorldY,
            int maxWorldYExclusive
    ) {
        Objects.requireNonNull(chunks, "chunks are required");
        return scan(
                chunks,
                catalog,
                center,
                radius,
                minWorldY,
                maxWorldYExclusive,
                RockChunkCoverage.fromParsedChunks(chunks)
        );
    }

    public RockMap scan(
            Collection<ParsedChunk> chunks,
            RockCatalog catalog,
            WorldPosition center,
            int radius,
            int minWorldY,
            int maxWorldYExclusive,
            RockChunkCoverage coverage
    ) {
        Objects.requireNonNull(chunks, "chunks are required");
        Objects.requireNonNull(catalog, "catalog is required");
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(coverage, "coverage is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (minWorldY >= maxWorldYExclusive) {
            throw new IllegalArgumentException(
                    "vertical scan range must not be empty"
            );
        }

        Map<ColumnCoordinate, List<ParsedChunk>> chunksByColumn =
                indexChunks(chunks);
        Map<ColumnCoordinate, List<ChunkSpan>> coverageByColumn =
                indexCoverage(coverage);
        int centerWorldX = floorBlockCoordinate(center.x());
        int centerWorldZ = floorBlockCoordinate(center.z());
        long radiusSquared = (long) radius * radius;
        List<RockColumnSample> columns = new ArrayList<>();

        for (long worldZ = (long) centerWorldZ - radius;
             worldZ <= (long) centerWorldZ + radius;
             worldZ++) {
            for (long worldX = (long) centerWorldX - radius;
                 worldX <= (long) centerWorldX + radius;
                 worldX++) {
                long dx = worldX - centerWorldX;
                long dz = worldZ - centerWorldZ;
                if (dx * dx + dz * dz > radiusSquared
                        || worldX < Integer.MIN_VALUE
                        || worldX > Integer.MAX_VALUE
                        || worldZ < Integer.MIN_VALUE
                        || worldZ > Integer.MAX_VALUE) {
                    continue;
                }

                int x = (int) worldX;
                int z = (int) worldZ;
                columns.add(
                        scanColumn(
                                x,
                                z,
                                chunksByColumn.get(
                                        new ColumnCoordinate(
                                                Math.floorDiv(x, ChunkCoordinate.SIZE_BLOCKS),
                                                Math.floorDiv(z, ChunkCoordinate.SIZE_BLOCKS)
                                        )
                                ),
                                coverageByColumn.get(
                                        new ColumnCoordinate(
                                                Math.floorDiv(x, ChunkCoordinate.SIZE_BLOCKS),
                                                Math.floorDiv(z, ChunkCoordinate.SIZE_BLOCKS)
                                        )
                                ),
                                catalog,
                                minWorldY,
                                maxWorldYExclusive
                        )
                );
            }
        }

        return RockMap.fromLegacySamples(
                center, radius, minWorldY, maxWorldYExclusive,
                RockMapMode.UPPER_ROCK, catalog, columns
        );
    }

    private int floorBlockCoordinate(double coordinate) {
        double floored = Math.floor(coordinate);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world coordinate is outside the supported block range"
            );
        }
        return (int) floored;
    }

    private Map<ColumnCoordinate, List<ParsedChunk>> indexChunks(
            Collection<ParsedChunk> chunks
    ) {
        Map<ColumnCoordinate, List<ParsedChunk>> indexed = new HashMap<>();
        for (ParsedChunk chunk : chunks) {
            Objects.requireNonNull(chunk, "chunks must not contain null");
            indexed.computeIfAbsent(
                    new ColumnCoordinate(chunk.coordinate().x(), chunk.coordinate().z()),
                    ignored -> new ArrayList<>()
            ).add(chunk);
        }
        return indexed;
    }

    private RockColumnSample scanColumn(
            int worldX,
            int worldZ,
            List<ParsedChunk> chunks,
            List<ChunkSpan> coverage,
            RockCatalog catalog,
            int minWorldY,
            int maxWorldYExclusive
    ) {
        if (coverage == null) {
            return RockColumnSample.unavailable(worldX, worldZ);
        }

        int localX = Math.floorMod(worldX, ChunkCoordinate.SIZE_BLOCKS);
        int localZ = Math.floorMod(worldZ, ChunkCoordinate.SIZE_BLOCKS);
        long nextY = maxWorldYExclusive;
        while (nextY > minWorldY) {
            ChunkSpan span = findCoverageSpan(coverage, nextY - 1);
            if (span == null) {
                return RockColumnSample.unavailable(worldX, worldZ);
            }

            long startY = Math.max(minWorldY, span.startY());
            ParsedChunk chunk = findChunk(
                    chunks,
                    span,
                    localX,
                    localZ
            );
            if (chunk != null) {
                for (long worldY = nextY - 1;
                     worldY >= startY;
                     worldY--) {
                    RockIdentity rock = catalog.findByBlockId(
                                    chunk.blockIdAt(
                                            localX,
                                            (int) worldY - chunk.minY(),
                                            localZ
                                    )
                            )
                            .orElse(null);
                    if (rock != null) {
                        return RockColumnSample.observed(
                                worldX,
                                worldZ,
                                rock,
                                (int) worldY
                        );
                    }
                }
            }
            nextY = startY;
        }

        return RockColumnSample.noRock(worldX, worldZ);
    }

    private ParsedChunk findChunk(
            List<ParsedChunk> chunks,
            ChunkSpan span,
            int localX,
            int localZ
    ) {
        if (chunks == null) {
            return null;
        }
        return chunks.stream()
                .filter(chunk -> localX < chunk.sizeX()
                        && localZ < chunk.sizeZ()
                        && chunk.minY() <= span.startY()
                        && (long) chunk.minY() + chunk.sizeY() >= span.endY())
                .findFirst()
                .orElse(null);
    }

    private ChunkSpan findCoverageSpan(
            List<ChunkSpan> coverage,
            long worldY
    ) {
        for (ChunkSpan span : coverage) {
            if (span.contains(worldY)) {
                return span;
            }
        }
        return null;
    }

    private Map<ColumnCoordinate, List<ChunkSpan>> indexCoverage(
            RockChunkCoverage coverage
    ) {
        Map<ColumnCoordinate, List<ChunkSpan>> indexed = new HashMap<>();
        for (ChunkCoordinate coordinate : coverage.availableChunks()) {
            indexed.computeIfAbsent(
                    new ColumnCoordinate(coordinate.x(), coordinate.z()),
                    ignored -> new ArrayList<>()
            ).add(
                    new ChunkSpan(
                            (long) coordinate.y() * ChunkCoordinate.SIZE_BLOCKS,
                            ((long) coordinate.y() + 1) * ChunkCoordinate.SIZE_BLOCKS
                    )
            );
        }
        return indexed;
    }

    private record ColumnCoordinate(int x, int z) {
    }

    private record ChunkSpan(long startY, long endY) {
        private boolean contains(long worldY) {
            return startY <= worldY && worldY < endY;
        }
    }
}
