package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.MapChunkHeightView;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Complete Surface streaming session. Mapchunks plan, and every decoded chunk is
 * consumed directly into one compact accumulator before the callback returns.
 */
public final class SurfaceStreamingSession {
    private final SurfaceRainHeightPlanner.StreamingSession planner;
    private final MapChunkCoordinate[] requestedMapChunks;
    private final Map<Integer, BlockInfo> registry;
    private final boolean ignoreFoliage;
    private final boolean requireLiquidLayer;
    private SurfaceRainHeightScanner.StreamingSession scanner;
    private boolean planned;
    private boolean finished;

    private SurfaceStreamingSession(
            SurfaceRainHeightPlanner.StreamingSession planner,
            MapChunkCoordinate[] requestedMapChunks,
            Map<Integer, BlockInfo> registry,
            boolean ignoreFoliage,
            boolean requireLiquidLayer
    ) {
        this.planner = planner;
        this.requestedMapChunks = requestedMapChunks;
        this.registry = registry;
        this.ignoreFoliage = ignoreFoliage;
        this.requireLiquidLayer = requireLiquidLayer;
    }

    public static SurfaceStreamingSession begin(
            WorldMetadata metadata,
            double centerX,
            double centerZ,
            int radius,
            Collection<MapChunkCoordinate> requestedMapChunks,
            Map<Integer, BlockInfo> registry,
            boolean ignoreFoliage,
            boolean requireLiquidLayer
    ) {
        Objects.requireNonNull(requestedMapChunks, "requested mapchunks are required");
        MapChunkCoordinate[] requested = requestedMapChunks.toArray(MapChunkCoordinate[]::new);
        for (MapChunkCoordinate coordinate : requested) {
            Objects.requireNonNull(coordinate, "requested mapchunks cannot contain null");
        }
        return new SurfaceStreamingSession(
                new SurfaceRainHeightPlanner().begin(metadata, centerX, centerZ, radius),
                requested,
                Objects.requireNonNull(registry, "registry is required"),
                ignoreFoliage,
                requireLiquidLayer
        );
    }

    public static SurfaceStreamingSession beginForMapChunks(
            WorldMetadata metadata,
            Collection<MapChunkCoordinate> requestedMapChunks,
            Map<Integer, BlockInfo> registry,
            boolean ignoreFoliage,
            boolean requireLiquidLayer
    ) {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(requestedMapChunks, "requested mapchunks are required");
        MapChunkCoordinate[] requested = requestedMapChunks.toArray(
                MapChunkCoordinate[]::new
        );
        if (requested.length == 0) {
            throw new IllegalArgumentException(
                    "requested mapchunks cannot be empty"
            );
        }
        for (MapChunkCoordinate coordinate : requested) {
            Objects.requireNonNull(
                    coordinate,
                    "requested mapchunks cannot contain null"
            );
        }
        return new SurfaceStreamingSession(
                new SurfaceRainHeightPlanner().beginForMapChunks(
                        metadata,
                        java.util.List.of(requested)
                ),
                requested,
                Objects.requireNonNull(registry, "registry is required"),
                ignoreFoliage,
                requireLiquidLayer
        );
    }

    public void acceptMapChunk(MapChunkHeightView mapChunk) {
        ensurePlanning();
        planner.accept(mapChunk);
    }

    public SurfaceRainHeightPlan finishPlanning() {
        ensurePlanning();
        for (MapChunkCoordinate coordinate : requestedMapChunks) {
            planner.promoteMissing(coordinate);
        }
        SurfaceRainHeightPlan plan = planner.finish();
        scanner = new SurfaceRainHeightScanner().begin(
                plan, registry, ignoreFoliage, requireLiquidLayer);
        planned = true;
        return plan;
    }

    public void acceptFastChunk(ParsedChunk chunk) {
        ensurePlanned();
        scanner.accept(chunk);
    }

    public void acceptFallbackChunk(ParsedChunk chunk) {
        ensurePlanned();
        scanner.acceptFallback(chunk);
    }

    public void acceptCachedTile(CachedSurfaceTileView tile) {
        ensurePlanned();
        scanner.acceptCachedTile(tile);
    }

    public java.util.List<MapChunkCoordinate> fallbackMapChunks() {
        ensurePlanned();
        scanner.promoteUndeliveredTargets();
        return scanner.fallbackMapChunks();
    }

    public SurfaceRainHeightScanResult finish() {
        ensurePlanned();
        finished = true;
        return scanner.finish();
    }

    private void ensurePlanning() {
        if (planned || finished) {
            throw new IllegalStateException("Surface planning phase is complete");
        }
    }

    private void ensurePlanned() {
        if (!planned || finished) {
            throw new IllegalStateException("Surface session is not accepting chunks");
        }
    }
}
