package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RainHeightSurfaceScanner {

    public StreamingSession begin(
            RainHeightSurfacePlan plan,
            Map<Integer, BlockInfo> registry,
            boolean ignoreFoliage,
            boolean requireLiquidLayer
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(registry, "registry is required");
        return new StreamingSession(
                plan,
                registry,
                ignoreFoliage,
                requireLiquidLayer
        );
    }

    public static final class StreamingSession {
        private static final Comparator<RainHeightSurfaceTarget> TARGET_ORDER =
                Comparator.comparingInt(RainHeightSurfaceTarget::worldZ)
                        .thenComparingInt(RainHeightSurfaceTarget::worldX)
                        .thenComparingInt(RainHeightSurfaceTarget::worldY);
        private static final Comparator<SurfaceBlock> BLOCK_ORDER =
                Comparator.comparingInt(SurfaceBlock::worldZ)
                        .thenComparingInt(SurfaceBlock::worldX)
                        .thenComparingInt(SurfaceBlock::y);

        private final Map<ChunkPosition, List<RainHeightSurfaceTarget>> targetsByChunk;
        private final Map<Integer, BlockInfo> registry;
        private final boolean ignoreFoliage;
        private final boolean requireLiquidLayer;
        private final Set<ChunkPosition> deliveredChunks = new HashSet<>();
        private final Set<RainHeightSurfaceTarget> resolvedTargets = new HashSet<>();
        private final Set<RainHeightSurfaceTarget> unresolvedTargets = new HashSet<>();
        private final List<SurfaceBlock> blocks = new ArrayList<>();
        private final SurfaceClassifier classifier = new SurfaceClassifier();

        private StreamingSession(
                RainHeightSurfacePlan plan,
                Map<Integer, BlockInfo> registry,
                boolean ignoreFoliage,
                boolean requireLiquidLayer
        ) {
            this.registry = registry;
            this.ignoreFoliage = ignoreFoliage;
            this.requireLiquidLayer = requireLiquidLayer;
            this.targetsByChunk = new HashMap<>();
            for (RainHeightSurfaceTarget target : plan.targets()) {
                targetsByChunk.computeIfAbsent(
                        target.chunkPosition(),
                        ignored -> new ArrayList<>()
                ).add(target);
            }
        }

        public void accept(ParsedChunk chunk) {
            Objects.requireNonNull(chunk, "chunk is required");
            ChunkPosition position = new ChunkPosition(
                    chunk.coordinate().x(),
                    chunk.coordinate().y(),
                    chunk.coordinate().z(),
                    0
            );
            if (!deliveredChunks.add(position)) {
                return;
            }

            List<RainHeightSurfaceTarget> targets = targetsByChunk.get(position);
            if (targets == null) {
                return;
            }

            for (RainHeightSurfaceTarget target : targets) {
                resolveTarget(chunk, target);
            }
        }

        public RainHeightSurfaceScanResult finish() {
            List<RainHeightSurfaceTarget> unresolved = targetsByChunk.values().stream()
                    .flatMap(List::stream)
                    .filter(target -> !resolvedTargets.contains(target))
                    .sorted(TARGET_ORDER)
                    .toList();
            return new RainHeightSurfaceScanResult(
                    blocks.stream().sorted(BLOCK_ORDER).toList(),
                    unresolved
            );
        }

        private void resolveTarget(
                ParsedChunk chunk,
                RainHeightSurfaceTarget target
        ) {
            if (resolvedTargets.contains(target)
                    || unresolvedTargets.contains(target)) {
                return;
            }

            int localX = target.worldX()
                    - chunk.coordinate().x() * ChunkCoordinate.SIZE_BLOCKS;
            int localZ = target.worldZ()
                    - chunk.coordinate().z() * ChunkCoordinate.SIZE_BLOCKS;
            int localY = target.worldY() - chunk.minY();

            if (localX < 0 || localX >= chunk.sizeX()
                    || localY < 0 || localY >= chunk.sizeY()
                    || localZ < 0 || localZ >= chunk.sizeZ()) {
                markUnresolved(target);
                return;
            }

            if (requireLiquidLayer && !chunk.liquidLayerAvailable()) {
                markUnresolved(target);
                return;
            }

            int blockId = chunk.blockIdAt(localX, localY, localZ);
            int liquidId = chunk.liquidLayerAvailable()
                    ? chunk.liquidIdAt(localX, localY, localZ)
                    : 0;
            BlockInfo blockInfo = registry.getOrDefault(
                    blockId,
                    BlockInfo.unknown(blockId)
            );
            BlockInfo liquidInfo = registry.getOrDefault(
                    liquidId,
                    BlockInfo.unknown(liquidId)
            );
            SurfaceClass surfaceClass = classifier.classify(blockInfo, liquidInfo);

            if (surfaceClass != SurfaceClass.WATER
                    && (blockInfo.isAir()
                    || ignoreFoliage && blockInfo.isFoliage())) {
                markUnresolved(target);
                return;
            }

            recordResolved(target, new SurfaceBlock(
                    target.worldX(),
                    target.worldY(),
                    target.worldZ(),
                    blockInfo,
                    liquidId,
                    liquidInfo,
                    surfaceClass
            ));
        }

        private void recordResolved(RainHeightSurfaceTarget target, SurfaceBlock block) {
            resolvedTargets.add(target);
            blocks.add(block);
        }

        private void markUnresolved(RainHeightSurfaceTarget target) {
            unresolvedTargets.add(target);
        }
    }

}
