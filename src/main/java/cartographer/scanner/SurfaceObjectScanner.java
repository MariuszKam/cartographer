package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scans planned terrain/object candidates without changing terrain-surface semantics. */
public final class SurfaceObjectScanner {
    public SurfaceObjectScanResult scan(
            RainHeightSurfacePlan plan,
            cartographer.model.WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            Set<Integer> wantedBlockIds,
            List<ParsedChunk> decodedChunks,
            Set<ChunkPosition> availablePositions
    ) {
        List<SurfaceObjectTarget> targets = plan.targets().stream()
                .map(target -> new SurfaceObjectTarget(
                        target.worldX(),
                        target.worldZ(),
                        java.util.stream.IntStream.range(
                                        target.worldY(),
                                        Math.min(
                                                metadata.mapSizeY(),
                                                target.worldY() + 4
                                        )
                                )
                                .boxed()
                                .toList()
                ))
                .toList();
        return scan(
                new SurfaceObjectPlan(targets, plan.chunkPositions()),
                registry,
                wantedBlockIds,
                decodedChunks,
                availablePositions
        );
    }

    public SurfaceObjectScanResult scan(
            SurfaceObjectPlan plan,
            Map<Integer, BlockInfo> registry,
            Set<Integer> wantedBlockIds,
            List<ParsedChunk> decodedChunks,
            Set<ChunkPosition> availablePositions
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(wantedBlockIds, "wanted block ids are required");
        Objects.requireNonNull(decodedChunks, "decoded chunks are required");
        Objects.requireNonNull(availablePositions, "available positions are required");

        Map<ChunkPosition, ParsedChunk> chunksByPosition = new HashMap<>();
        for (ParsedChunk chunk : decodedChunks) {
            Objects.requireNonNull(chunk, "decoded chunks cannot contain null");
            chunksByPosition.put(positionOf(chunk), chunk);
        }

        List<SurfaceBlock> blocks = new ArrayList<>();
        int unavailable = 0;
        int notObserved = 0;
        for (SurfaceObjectTarget target : plan.targets()) {
            boolean targetUnavailable = false;
            boolean observed = false;
            for (int worldY : target.candidateWorldYs()) {
                ChunkPosition position = target.chunkPosition(worldY);
                if (!availablePositions.contains(position)) {
                    targetUnavailable = true;
                    continue;
                }
                ParsedChunk chunk = chunksByPosition.get(position);
                if (chunk == null) {
                    continue;
                }
                int localY = worldY - chunk.minY();
                if (localY < 0 || localY >= chunk.sizeY()) {
                    targetUnavailable = true;
                    continue;
                }
                int blockId = chunk.blockIdAt(
                        Math.floorMod(target.worldX(), chunk.sizeX()),
                        localY,
                        Math.floorMod(target.worldZ(), chunk.sizeZ())
                );
                if (wantedBlockIds.contains(blockId)) {
                    blocks.add(new SurfaceBlock(
                            target.worldX(),
                            worldY,
                            target.worldZ(),
                            registry.getOrDefault(blockId, BlockInfo.unknown(blockId)),
                            0,
                            BlockInfo.unknown(0),
                            SurfaceClass.UNKNOWN
                    ));
                    observed = true;
                    break;
                }
            }
            if (!observed && targetUnavailable) {
                unavailable++;
            } else if (!observed) {
                notObserved++;
            }
        }

        blocks.sort(Comparator.comparingInt(SurfaceBlock::worldZ)
                .thenComparingInt(SurfaceBlock::worldX)
                .thenComparingInt(SurfaceBlock::y));
        return new SurfaceObjectScanResult(
                blocks,
                plan.targets().size(),
                unavailable,
                blocks.size(),
                notObserved
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
