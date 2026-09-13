package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
        Objects.requireNonNull(catalog, "catalog is required");
        Objects.requireNonNull(center, "center is required");
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
        int centerWorldX = (int) Math.round(center.x());
        int centerWorldZ = (int) Math.round(center.z());
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
                                catalog,
                                minWorldY,
                                maxWorldYExclusive
                        )
                );
            }
        }

        return new RockMap(center, radius, columns);
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
            RockCatalog catalog,
            int minWorldY,
            int maxWorldYExclusive
    ) {
        if (chunks == null) {
            return RockColumnSample.unavailable(worldX, worldZ);
        }

        int localX = Math.floorMod(worldX, ChunkCoordinate.SIZE_BLOCKS);
        int localZ = Math.floorMod(worldZ, ChunkCoordinate.SIZE_BLOCKS);
        List<ParsedChunk> covering = chunks.stream()
                .filter(chunk -> localX < chunk.sizeX()
                        && localZ < chunk.sizeZ())
                .sorted(Comparator.comparingInt(ParsedChunk::minY).reversed())
                .toList();

        if (!coversRange(covering, minWorldY, maxWorldYExclusive)) {
            return RockColumnSample.unavailable(worldX, worldZ);
        }

        for (ParsedChunk chunk : covering) {
            int startY = Math.max(minWorldY, chunk.minY());
            int endY = Math.min(
                    maxWorldYExclusive,
                    chunk.minY() + chunk.sizeY()
            );
            for (int worldY = endY - 1; worldY >= startY; worldY--) {
                RockIdentity rock = catalog.findByBlockId(
                                chunk.blockIdAt(
                                        localX,
                                        worldY - chunk.minY(),
                                        localZ
                                )
                        )
                        .orElse(null);
                if (rock != null) {
                    return RockColumnSample.observed(
                            worldX,
                            worldZ,
                            rock,
                            worldY
                    );
                }
            }
        }

        return RockColumnSample.noRock(worldX, worldZ);
    }

    private boolean coversRange(
            List<ParsedChunk> chunks,
            int minWorldY,
            int maxWorldYExclusive
    ) {
        int coveredUntil = minWorldY;
        for (ParsedChunk chunk : chunks.stream()
                .sorted(Comparator.comparingInt(ParsedChunk::minY))
                .toList()) {
            if (chunk.minY() > coveredUntil) {
                return false;
            }
            coveredUntil = Math.max(
                    coveredUntil,
                    chunk.minY() + chunk.sizeY()
            );
            if (coveredUntil >= maxWorldYExclusive) {
                return true;
            }
        }
        return false;
    }

    private record ColumnCoordinate(int x, int z) {
    }
}
