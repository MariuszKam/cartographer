package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClassCode;

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
                new SurfaceRegistryLookup(Objects.requireNonNull(registry, "registry is required")),
                ignoreFoliage,
                requireLiquidLayer
        );
    }

    public static final class StreamingSession {
        private final SurfaceRainHeightPlan plan;
        private final SurfaceRegistryLookup registry;
        private final boolean ignoreFoliage;
        private final boolean requireLiquidLayer;
        private final SurfaceTileAccumulator accumulator;
        private final Set<ChunkPosition> delivered = new HashSet<>();
        private final Set<ChunkPosition> deliveredFallback = new HashSet<>();
        private final Set<MapChunkCoordinate> sourceMapChunksLoaded = new HashSet<>();
        private final SurfaceFallbackDiagnosticState fallbackDiagnostics =
                new SurfaceFallbackDiagnosticState();
        private final Set<MapChunkCoordinate> cachedTiles = new HashSet<>();
        private final boolean[] promoted;
        private boolean finished;
        private int cachedColumns;
        private int cachedEmptyColumns;
        private int cachedLiquidUnavailable;

        private StreamingSession(
                SurfaceRainHeightPlan plan,
                SurfaceRegistryLookup registry,
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
                    accumulator.resetForFallbackTile(
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
            sourceMapChunksLoaded.add(new MapChunkCoordinate(
                    chunk.coordinate().x(), chunk.coordinate().z()));
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
                    accumulator.markLiquidUnavailable(worldX, worldZ);
                    promote(tileIndex);
                    continue;
                }
                int blockId = chunk.blockIdAt(localX, localY, localZ);
                int liquidId = chunk.liquidLayerAvailable()
                        ? chunk.liquidIdAt(localX, localY, localZ) : 0;
                SurfaceClass surfaceClass = registry.classify(blockId, liquidId);
                if (surfaceClass != SurfaceClass.WATER
                        && (registry.isAir(blockId) || ignoreFoliage && registry.isFoliage(blockId))) {
                    promote(tileIndex);
                    continue;
                }
                accumulator.recordSurface(
                        worldX, worldZ, worldY, blockId, liquidId, surfaceClass);
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
            byte[] diagnosticColumns = fallbackDiagnostics.considerChunk(chunk);
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
            sourceMapChunksLoaded.add(new MapChunkCoordinate(
                    chunk.coordinate().x(), chunk.coordinate().z()));
            for (int localZ = 0; localZ < chunk.sizeZ(); localZ++) {
                for (int localX = 0; localX < chunk.sizeX(); localX++) {
                    int worldX = chunk.worldX(localX);
                    int worldZ = chunk.worldZ(localZ);
                    boolean active = plan.layout().isActive(worldX, worldZ);
                    if (active) {
                        accumulator.consider(worldX, worldZ);
                        if (!chunk.liquidLayerAvailable()) {
                            accumulator.markLiquidUnavailable(worldX, worldZ);
                        }
                    }
                    for (int localY = chunk.sizeY() - 1; localY >= 0; localY--) {
                        int blockId = chunk.blockIdAt(localX, localY, localZ);
                        int liquidId = chunk.liquidLayerAvailable()
                                ? chunk.liquidIdAt(localX, localY, localZ) : 0;
                        SurfaceClass surfaceClass = registry.classify(blockId, liquidId);
                        if (surfaceClass != SurfaceClass.WATER
                                && (registry.isAir(blockId)
                                || ignoreFoliage && registry.isFoliage(blockId))) {
                            continue;
                        }
                        fallbackDiagnostics.markResolved(diagnosticColumns, localX, localZ);
                        if (!active) {
                            break;
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

        /** Injects one validated full-mapchunk cache artifact into this request accumulator. */
        public void acceptCachedTile(CachedSurfaceTileView tile) {
            ensureMutable();
            Objects.requireNonNull(tile, "cached Surface tile is required");
            MapChunkCoordinate coordinate = tile.coordinate();
            if (!cachedTiles.add(coordinate)) {
                return;
            }
            int width = tile.width();
            int height = tile.height();
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "cached Surface tile geometry must be positive"
                );
            }
            plan.layout().tileIndex(coordinate.x(), coordinate.z());
            if (width != plan.layout().tileWidth(coordinate.x())
                    || height != plan.layout().tileHeight(coordinate.z())) {
                throw new IllegalArgumentException(
                        "cached Surface tile geometry does not match request world"
                );
            }
            for (int localZ = 0; localZ < height; localZ++) {
                for (int localX = 0; localX < width; localX++) {
                    int cellIndex = localZ * width + localX;
                    int worldX = plan.layout().worldXForTileLocal(coordinate.x(), localX);
                    int worldZ = plan.layout().worldZForTileLocal(coordinate.z(), localZ);
                    byte cellState = tile.stateAtIndex(cellIndex);
                    if ((cellState & SurfaceTile.CONSIDERED) != 0) {
                        accumulator.consider(worldX, worldZ);
                    }
                    if ((cellState & SurfaceTile.LIQUID_UNAVAILABLE) != 0) {
                        accumulator.markLiquidUnavailable(worldX, worldZ);
                    }
                    if ((cellState & SurfaceTile.RESOLVED) != 0) {
                        accumulator.recordSurface(
                                worldX,
                                worldZ,
                                tile.surfaceYAtIndex(cellIndex),
                                tile.blockIdAtIndex(cellIndex),
                                tile.liquidBlockIdAtIndex(cellIndex),
                                SurfaceClassCode.decode(
                                        tile.surfaceClassCodeAtIndex(cellIndex)
                                )
                        );
                    }
                    if (!tile.fallbackMode()
                            && plan.layout().isActive(worldX, worldZ)
                            && (cellState & SurfaceTile.CONSIDERED) != 0) {
                        cachedColumns++;
                    }
                }
            }
            if (tile.fallbackMode()) {
                cachedColumns = Math.addExact(
                        cachedColumns,
                        tile.diagnosticColumnsScanned()
                );
                cachedEmptyColumns = Math.addExact(
                        cachedEmptyColumns,
                        tile.diagnosticEmptyColumns()
                );
                cachedLiquidUnavailable = Math.addExact(
                        cachedLiquidUnavailable,
                        tile.diagnosticLiquidUnavailableColumns()
                );
            }
        }

        /** Promotes targets whose requested server chunk was not delivered. */
        public void promoteUndeliveredTargets() {
            ensureMutable();
            for (ChunkPosition position : plan.chunkPositions()) {
                if (delivered.contains(position)) {
                    continue;
                }
                long[] ordinals = plan.cellOrdinalsFor(position);
                if (ordinals == null) {
                    continue;
                }
                for (long ordinal : ordinals) {
                    promote((int) (ordinal >>> 32));
                }
            }
        }

        public int healthyFastColumns() {
            int count = 0;
            for (int tileIndex = 0; tileIndex < promoted.length; tileIndex++) {
                if (promoted[tileIndex]) {
                    continue;
                }
                int tileX = plan.layout().tileXAt(tileIndex);
                int tileZ = plan.layout().tileZAt(tileIndex);
                int width = plan.layout().tileWidth(tileX);
                int height = plan.layout().tileHeight(tileZ);
                for (int localZ = 0; localZ < height; localZ++) {
                    for (int localX = 0; localX < width; localX++) {
                        int cell = plan.layout().cellIndex(tileX, tileZ, localX, localZ);
                        if (plan.hasCandidate(tileIndex, cell)) {
                            count = Math.addExact(count, 1);
                        }
                    }
                }
            }
            return count;
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
            SurfaceFallbackDiagnosticState.Summary fallbackSummary = fallbackDiagnostics.summary();
            int healthyFast = healthyFastColumns();
            return new SurfaceRainHeightScanResult(
                    accumulator.finish(), fallback,
                    new SurfaceRainHeightDiagnosticCounters(
                            Math.addExact(Math.addExact(healthyFast, fallbackSummary.consideredColumns()),
                                    cachedColumns),
                            Math.addExact(fallbackSummary.emptyColumns(), cachedEmptyColumns),
                            Math.addExact(fallbackSummary.liquidUnavailableColumns(),
                                    cachedLiquidUnavailable)),
                    fallbackDiagnostics.summariesByMapChunk(),
                    sourceMapChunksLoaded.size()
            );
        }

        public java.util.List<MapChunkCoordinate> fallbackMapChunks() {
            java.util.ArrayList<MapChunkCoordinate> fallback = new java.util.ArrayList<>();
            for (int tileIndex = 0; tileIndex < promoted.length; tileIndex++) {
                if (promoted[tileIndex]) {
                    fallback.add(new MapChunkCoordinate(
                            plan.layout().tileXAt(tileIndex), plan.layout().tileZAt(tileIndex)));
                }
            }
            fallback.sort(java.util.Comparator.comparingInt(MapChunkCoordinate::z)
                    .thenComparingInt(MapChunkCoordinate::x));
            return java.util.List.copyOf(fallback);
        }

        private void promote(int tileIndex) {
            if (!promoted[tileIndex]) {
                promoted[tileIndex] = true;
                accumulator.resetForFallbackTile(
                        plan.layout().tileXAt(tileIndex),
                        plan.layout().tileZAt(tileIndex)
                );
            }
        }

        private void ensureMutable() {
            if (finished) {
                throw new IllegalStateException("RainHeight scanner is finished");
            }
        }
    }
}
