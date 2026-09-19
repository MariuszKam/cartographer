package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.ChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Shared callback-driven compact Surface reader for non-render consumers. */
public final class ReadSurfaceMapUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final MapChunkPositionPlanner mapChunkPositionPlanner = new MapChunkPositionPlanner();
    private final SurfaceFallbackChunkPlanner fallbackChunkPlanner = new SurfaceFallbackChunkPlanner();

    public ReadSurfaceMapUseCase(VcdbsReader reader, WorldMetadataReader metadataReader) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadata reader is required");
    }

    public ReadSurfaceMapResult execute(ReadSurfaceMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public ReadSurfaceMapResult execute(ReadSurfaceMapRequest request, ProgressReporter progress) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        Path savePath = request.savePath();
        WorldMetadata metadata = metadataReader.read(savePath);
        int centerX = (int) Math.round(request.center().x());
        int centerZ = (int) Math.round(request.center().z());
        List<MapChunkCoordinate> positions = mapChunkPositionPlanner.plan(
                metadata, centerX, centerZ, request.radius());
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath, progress);
        ReadDiagnostics mapDiagnostics = new ReadDiagnostics();
        SurfaceStreamingSession session = SurfaceStreamingSession.begin(
                metadata, centerX, centerZ, request.radius(), positions, registry,
                request.ignoreFoliage(), request.requireLiquidLayer());
        reader.forEachMapChunkByCoordinate(savePath, positions, mapDiagnostics,
                session::acceptMapChunk, progress);

        SurfaceRainHeightPlan plan = session.finishPlanning();
        ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
        Set<ChunkPosition> liquidFailureChunks = new HashSet<>();
        ChunkStreamStats fast = emptyChunkStats();
        if (!plan.chunkPositions().isEmpty()) {
            fast = reader.forEachSurfaceChunkByPositionAdaptive(savePath, plan.chunkPositions(),
                    chunkDiagnostics,
                    chunk -> consumeSurfaceChunk(chunk, chunkDiagnostics, liquidFailureChunks,
                            session::acceptFastChunk), progress);
        }
        List<MapChunkCoordinate> fallbackMapChunks = session.fallbackMapChunks();
        List<ChunkPosition> fallbackPositions = fallbackChunkPlanner.plan(metadata, fallbackMapChunks);
        ChunkStreamStats fallback = emptyChunkStats();
        if (!fallbackPositions.isEmpty()) {
            fallback = reader.forEachSurfaceChunkByPositionAdaptive(savePath, fallbackPositions,
                    chunkDiagnostics,
                    chunk -> consumeSurfaceChunk(chunk, chunkDiagnostics, liquidFailureChunks,
                            session::acceptFallbackChunk), progress);
        }
        SurfaceRainHeightScanResult scan = session.finish();
        SurfaceRainHeightDiagnosticCounters counters = scan.diagnostics();
        int chunksScanned = Math.addExact(fast.parsedChunks(), fallback.parsedChunks());
        return new ReadSurfaceMapResult(new cartographer.scanner.SurfaceMapScanResult(
                scan.surface(), registry, chunksScanned, counters.columnsScanned(),
                counters.emptyColumns(), counters.liquidUnavailableColumns()),
                mapDiagnostics, chunkDiagnostics);
    }

    private void consumeSurfaceChunk(
            ParsedChunk chunk,
            ReadDiagnostics diagnostics,
            Set<ChunkPosition> liquidFailureChunks,
            Consumer<ParsedChunk> consumer
    ) {
        if (!chunk.liquidLayerAvailable()) {
            ChunkPosition position = new ChunkPosition(
                    chunk.coordinate().x(), chunk.coordinate().y(), chunk.coordinate().z(), 0);
            if (liquidFailureChunks.add(position)) {
                diagnostics.recordLiquidDecodeFailure(chunk.liquidDecodeError());
            }
        }
        consumer.accept(chunk);
    }

    private ChunkStreamStats emptyChunkStats() {
        return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
    }
}
