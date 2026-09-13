package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Finds registry-selected loose objects in the small window above rain height. */
public final class SurfaceObjectScanner {
    public static final int DEFAULT_WINDOW_HEIGHT = 4;

    public SurfaceObjectScanResult scan(
            RainHeightSurfacePlan plan,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            Set<Integer> wantedBlockIds,
            List<ParsedChunk> decodedChunks,
            Set<ChunkPosition> availablePositions
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(wantedBlockIds, "wanted block ids are required");
        Objects.requireNonNull(decodedChunks, "decoded chunks are required");
        Objects.requireNonNull(availablePositions, "available positions are required");

        Map<ChunkPosition, ParsedChunk> chunksByPosition = new HashMap<>();
        for (ParsedChunk chunk : decodedChunks) {
            Objects.requireNonNull(chunk, "decoded chunks cannot contain null");
            chunksByPosition.put(positionOf(chunk), chunk);
        }

        Set<Integer> wanted = Set.copyOf(wantedBlockIds);
        List<SurfaceBlock> blocks = new ArrayList<>();
        int unavailable = 0;
        for (RainHeightSurfaceTarget target : plan.targets()) {
            List<Integer> worldYs = windowYs(target.worldY(), metadata.mapSizeY());
            Set<ChunkPosition> required = new HashSet<>();
            for (int worldY : worldYs) {
                required.add(chunkPosition(target.worldX(), worldY, target.worldZ()));
            }

            boolean covered = required.stream().allMatch(availablePositions::contains);
            if (!covered) {
                unavailable++;
                continue;
            }

            for (int worldY : worldYs) {
                ChunkPosition position = chunkPosition(
                        target.worldX(), worldY, target.worldZ()
                );
                ParsedChunk chunk = chunksByPosition.get(position);
                if (chunk == null) {
                    continue;
                }
                int localX = Math.floorMod(target.worldX(), ChunkCoordinate.SIZE_BLOCKS);
                int localY = worldY - chunk.minY();
                int localZ = Math.floorMod(target.worldZ(), ChunkCoordinate.SIZE_BLOCKS);
                if (localY >= chunk.sizeY()) {
                    continue;
                }
                int blockId = chunk.blockIdAt(localX, localY, localZ);
                if (wanted.contains(blockId)) {
                    BlockInfo blockInfo = registry.getOrDefault(
                            blockId,
                            BlockInfo.unknown(blockId)
                    );
                    blocks.add(new SurfaceBlock(
                            target.worldX(),
                            worldY,
                            target.worldZ(),
                            blockInfo,
                            0,
                            BlockInfo.unknown(0),
                            SurfaceClass.UNKNOWN
                    ));
                    break;
                }
            }
        }

        blocks.sort(Comparator.comparingInt(SurfaceBlock::worldZ)
                .thenComparingInt(SurfaceBlock::worldX)
                .thenComparingInt(SurfaceBlock::y));
        return new SurfaceObjectScanResult(blocks, plan.targets().size(), unavailable);
    }

    public List<ChunkPosition> chunkPositions(
            RainHeightSurfacePlan plan,
            WorldMetadata metadata
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Set<ChunkPosition> positions = new HashSet<>();
        for (RainHeightSurfaceTarget target : plan.targets()) {
            for (int worldY : windowYs(target.worldY(), metadata.mapSizeY())) {
                positions.add(chunkPosition(target.worldX(), worldY, target.worldZ()));
            }
        }
        return positions.stream()
                .sorted(Comparator.comparingInt(ChunkPosition::y)
                        .thenComparingInt(ChunkPosition::z)
                        .thenComparingInt(ChunkPosition::x))
                .toList();
    }

    private List<Integer> windowYs(int rainHeight, int mapSizeY) {
        int end = Math.min(
                mapSizeY,
                Math.addExact(rainHeight, DEFAULT_WINDOW_HEIGHT)
        );
        List<Integer> result = new ArrayList<>();
        for (int y = Math.max(0, rainHeight); y < end; y++) {
            result.add(y);
        }
        return result;
    }

    private ChunkPosition chunkPosition(int worldX, int worldY, int worldZ) {
        return new ChunkPosition(
                Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS),
                Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS),
                Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS),
                0
        );
    }

    private ChunkPosition positionOf(ParsedChunk chunk) {
        return new ChunkPosition(
                chunk.coordinate().x(),
                chunk.coordinate().y(),
                chunk.coordinate().z(),
                0
        );
    }
}
