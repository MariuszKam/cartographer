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

public final class RockAtYScanner {
    public RockMap scan(
            Collection<ParsedChunk> chunks,
            RockCatalog catalog,
            RockChunkCoverage coverage,
            WorldPosition center,
            int radius,
            int worldY
    ) {
        Objects.requireNonNull(chunks, "chunks are required");
        Objects.requireNonNull(catalog, "catalog is required");
        Objects.requireNonNull(coverage, "coverage is required");
        Objects.requireNonNull(center, "center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }

        Map<ChunkCoordinate, ParsedChunk> decoded = indexChunks(chunks);
        int centerX = floorBlockCoordinate(center.x());
        int centerZ = floorBlockCoordinate(center.z());
        int chunkY = Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS);
        int localY = Math.floorMod(worldY, ChunkCoordinate.SIZE_BLOCKS);
        long radiusSquared = (long) radius * radius;
        List<RockColumnSample> samples = new ArrayList<>();

        for (long z = (long) centerZ - radius;
             z <= (long) centerZ + radius;
             z++) {
            for (long x = (long) centerX - radius;
                 x <= (long) centerX + radius;
                 x++) {
                long dx = x - centerX;
                long dz = z - centerZ;
                if (dx * dx + dz * dz > radiusSquared
                        || x < Integer.MIN_VALUE
                        || x > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE
                        || z > Integer.MAX_VALUE) {
                    continue;
                }

                int worldX = (int) x;
                int worldZ = (int) z;
                int chunkX = Math.floorDiv(
                        worldX,
                        ChunkCoordinate.SIZE_BLOCKS
                );
                int chunkZ = Math.floorDiv(
                        worldZ,
                        ChunkCoordinate.SIZE_BLOCKS
                );
                ChunkCoordinate required = new ChunkCoordinate(
                        chunkX,
                        chunkY,
                        chunkZ
                );
                if (!coverage.contains(required)) {
                    samples.add(RockColumnSample.unavailable(worldX, worldZ));
                    continue;
                }

                ParsedChunk chunk = decoded.get(required);
                if (chunk == null) {
                    samples.add(RockColumnSample.noRock(worldX, worldZ));
                    continue;
                }

                int localX = Math.floorMod(worldX, ChunkCoordinate.SIZE_BLOCKS);
                int localZ = Math.floorMod(worldZ, ChunkCoordinate.SIZE_BLOCKS);
                RockIdentity rock = catalog.findByBlockId(
                                chunk.blockIdAt(localX, localY, localZ)
                        )
                        .orElse(null);
                if (rock == null) {
                    samples.add(RockColumnSample.noRock(worldX, worldZ));
                } else {
                    samples.add(
                            RockColumnSample.observed(
                                    worldX,
                                    worldZ,
                                    rock,
                                    worldY
                            )
                    );
                }
            }
        }

        return new RockMap(center, radius, samples);
    }

    private Map<ChunkCoordinate, ParsedChunk> indexChunks(
            Collection<ParsedChunk> chunks
    ) {
        Map<ChunkCoordinate, ParsedChunk> indexed = new HashMap<>();
        for (ParsedChunk chunk : chunks) {
            ParsedChunk required = Objects.requireNonNull(
                    chunk,
                    "chunks must not contain null"
            );
            indexed.put(
                    required.coordinate(),
                    required
            );
        }
        return indexed;
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

}
