package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.model.MapChunkCoordinate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Consumes one decoded RainHeight chunk directly into primitive tile state. */
public final class SurfaceRainHeightScanner {
    public StreamingSession begin(
            SurfaceRainHeightPlan plan,
            Map<Integer, BlockInfo> registry,
            boolean ignoreFoliage,
            boolean requireLiquidLayer
    ) {
        return new StreamingSession(
                Objects.requireNonNull(plan, "plan is required"),
                Objects.requireNonNull(registry, "registry is required"),
                ignoreFoliage,
                requireLiquidLayer
        );
    }

    public static final class StreamingSession {
        private final SurfaceRainHeightPlan plan;
        private final Map<Integer, BlockInfo> registry;
        private final boolean ignoreFoliage;
        private final boolean requireLiquidLayer;
        private final SurfaceTileAccumulator accumulator;
        private final SurfaceClassifier classifier = new SurfaceClassifier();
        private final Set<ChunkPosition> delivered = new HashSet<>();
        private final Set<ChunkPosition> deliveredFallback = new HashSet<>();
        private final boolean[] promoted;
        private boolean finished;
        private int resolved;
        private int unresolved;
        private int liquidUnavailable;

        private StreamingSession(
                SurfaceRainHeightPlan plan,
                Map<Integer, BlockInfo> registry,
                boolean ignoreFoliage,
                boolean requireLiquidLayer
        ) {
            this.plan = plan;
            this.registry = registry;
            this.ignoreFoliage = ignoreFoliage;
            this.requireLiquidLayer = requireLiquidLayer;
            this.accumulator = new SurfaceTileAccumulator(plan.layout());
            this.promoted = new boolean[plan.tileCount()];
            for (int tileIndex = 0; tileIndex < promoted.length; tileIndex++) {
                if (plan.isPromoted(tileIndex)) {
                    promoted[tileIndex] = true;
                    accumulator.clearResolvedForTile(
                            plan.layout().tileXAt(tileIndex),
                            plan.layout().tileZAt(tileIndex)
                    );
                }
            }
        }

        public void accept(ParsedChunk chunk) {
            ensureMutable();
            Objects.requireNonNull(chunk, "chunk is required");
            ChunkPosition position = new ChunkPosition(
                    chunk.coordinate().x(),
                    chunk.coordinate().y(),
                    chunk.coordinate().z(),
                    0
            );
            if (!delivered.add(position)) {
                return;
            }
            long[] ordinals = plan.cellOrdinalsFor(position);
            if (ordinals == null) {
                return;
            }
            for (long ordinal : ordinals) {
                int tileIndex = (int) (ordinal >>> 32);
                int cellIndex = (int) ordinal;
                if (promoted[tileIndex]) {
                    continue;
                }
                int tileX = plan.layout().tileXAt(tileIndex);
                int tileZ = plan.layout().tileZAt(tileIndex);
                int width = plan.layout().tileWidth(tileX);
                int localX = cellIndex % width;
                int localZ = cellIndex / width;
                int worldX = plan.layout().worldXForTileLocal(tileX, localX);
                int worldZ = plan.layout().worldZForTileLocal(tileZ, localZ);
                int worldY = plan.rainHeightAt(tileIndex, cellIndex);
                int localY = worldY - chunk.minY();
                if (localX < 0 || localX >= chunk.sizeX()
                        || localY < 0 || localY >= chunk.sizeY()
                        || localZ < 0 || localZ >= chunk.sizeZ()) {
                    promote(tileIndex);
                    continue;
                }
                if (requireLiquidLayer && !chunk.liquidLayerAvailable()) {
                    liquidUnavailable++;
                    promote(tileIndex);
                    continue;
                }
                int blockId = chunk.blockIdAt(localX, localY, localZ);
                int liquidId = chunk.liquidLayerAvailable()
                        ? chunk.liquidIdAt(localX, localY, localZ) : 0;
                BlockInfo blockInfo = registry.get(blockId);
                if (blockInfo == null) {
                    blockInfo = BlockInfo.unknown(blockId);
                }
                BlockInfo liquidInfo = registry.get(liquidId);
                if (liquidInfo == null) {
                    liquidInfo = BlockInfo.unknown(liquidId);
                }
                SurfaceClass surfaceClass = classifier.classify(blockInfo, liquidInfo);
                if (surfaceClass != SurfaceClass.WATER
                        && (blockInfo.isAir() || ignoreFoliage && blockInfo.isFoliage())) {
                    promote(tileIndex);
                    continue;
                }
                accumulator.recordSurface(
                        worldX, worldZ, worldY, blockId, liquidId, surfaceClass);
                resolved++;
            }
        }

        /** Consumes one promoted-mapchunk fallback chunk without retaining it. */
        public void acceptFallback(ParsedChunk chunk) {
            ensureMutable();
            Objects.requireNonNull(chunk, "chunk is required");
            ChunkPosition position = new ChunkPosition(
                    chunk.coordinate().x(),
                    chunk.coordinate().y(),
                    chunk.coordinate().z(),
                    0
            );
            if (!deliveredFallback.add(position)) {
                return;
            }
            int tileIndex;
            try {
                tileIndex = plan.layout().tileIndex(
                        chunk.coordinate().x(), chunk.coordinate().z());
            } catch (IndexOutOfBoundsException ignored) {
                return;
            }
            if (!promoted[tileIndex]) {
                return;
            }
            int tileX = plan.layout().tileXAt(tileIndex);
            int tileZ = plan.layout().tileZAt(tileIndex);
            int width = plan.layout().tileWidth(tileX);
            int height = plan.layout().tileHeight(tileZ);
            for (int localZ = 0; localZ < height; localZ++) {
                for (int localX = 0; localX < width; localX++) {
                    int worldX = plan.layout().worldXForTileLocal(tileX, localX);
                    int worldZ = plan.layout().worldZForTileLocal(tileZ, localZ);
                    if (!plan.layout().isActive(worldX, worldZ)) {
                        continue;
                    }
                    accumulator.consider(worldX, worldZ);
                    if (!chunk.liquidLayerAvailable()) {
                        accumulator.markLiquidUnavailable(worldX, worldZ);
                        liquidUnavailable++;
                    }
                    for (int localY = chunk.sizeY() - 1; localY >= 0; localY--) {
                        int blockId = chunk.blockIdAt(localX, localY, localZ);
                        int liquidId = chunk.liquidLayerAvailable()
                                ? chunk.liquidIdAt(localX, localY, localZ) : 0;
                        BlockInfo blockInfo = registry.get(blockId);
                        if (blockInfo == null) {
                            blockInfo = BlockInfo.unknown(blockId);
                        }
                        BlockInfo liquidInfo = registry.get(liquidId);
                        if (liquidInfo == null) {
                            liquidInfo = BlockInfo.unknown(liquidId);
                        }
                        SurfaceClass surfaceClass = classifier.classify(blockInfo, liquidInfo);
                        if (surfaceClass != SurfaceClass.WATER
                                && (blockInfo.isAir()
                                || ignoreFoliage && blockInfo.isFoliage())) {
                            continue;
                        }
                        accumulator.recordSurface(
                                worldX,
                                worldZ,
                                chunk.worldY(localY),
                                blockId,
                                liquidId,
                                surfaceClass
                        );
                        break;
                    }
                }
            }
        }

        public SurfaceRainHeightScanResult finish() {
            ensureMutable();
            finished = true;
            java.util.ArrayList<cartographer.model.MapChunkCoordinate> fallback =
                    new java.util.ArrayList<>();
            for (int tileIndex = 0; tileIndex < promoted.length; tileIndex++) {
                if (promoted[tileIndex]) {
                    fallback.add(new cartographer.model.MapChunkCoordinate(
                            plan.layout().tileXAt(tileIndex), plan.layout().tileZAt(tileIndex)));
                }
            }
            fallback.sort(java.util.Comparator.comparingInt(
                            cartographer.model.MapChunkCoordinate::z)
                    .thenComparingInt(cartographer.model.MapChunkCoordinate::x));
            return new SurfaceRainHeightScanResult(
                    accumulator.finish(), fallback, resolved, unresolved, liquidUnavailable);
        }

        private void promote(int tileIndex) {
            if (!promoted[tileIndex]) {
                promoted[tileIndex] = true;
                accumulator.clearResolvedForTile(
                        plan.layout().tileXAt(tileIndex),
                        plan.layout().tileZAt(tileIndex)
                );
                unresolved++;
            }
        }

        private void ensureMutable() {
            if (finished) {
                throw new IllegalStateException("RainHeight scanner is finished");
            }
        }
    }
}
