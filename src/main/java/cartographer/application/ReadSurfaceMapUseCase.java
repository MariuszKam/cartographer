package cartographer.application;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.ChunkStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceFallbackChunkPlanner;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightPlan;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Shared callback-driven compact Surface reader for non-render consumers. */
public final class ReadSurfaceMapUseCase {
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final MapChunkPositionPlanner mapChunkPositionPlanner = new MapChunkPositionPlanner();
    private final SurfaceFallbackChunkPlanner fallbackChunkPlanner = new SurfaceFallbackChunkPlanner();

    public ReadSurfaceMapUseCase(VcdbsReader reader, WorldMetadataReader metadataReader) {
        this(
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                )
        );
    }

    ReadSurfaceMapUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
    }

    public ReadSurfaceMapResult execute(ReadSurfaceMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public ReadSurfaceMapResult execute(ReadSurfaceMapRequest request, ProgressReporter progress) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            WorldMetadata metadata = saveSession.snapshot().metadata();
            Map<Integer, BlockInfo> registry = saveSession.snapshot().blockRegistry();
            int centerX = (int) Math.round(request.center().x());
            int centerZ = (int) Math.round(request.center().z());
            List<MapChunkCoordinate> positions = mapChunkPositionPlanner.plan(
                    metadata, centerX, centerZ, request.radius());
            ReadDiagnostics mapDiagnostics = new ReadDiagnostics();
            SurfaceStreamingSession surfaceSession = SurfaceStreamingSession.begin(
                    metadata, centerX, centerZ, request.radius(), positions, registry,
                    request.ignoreFoliage(), request.requireLiquidLayer());
            reader.forEachMapChunkByCoordinate(
                    saveSession,
                    positions,
                    mapDiagnostics,
                    surfaceSession::acceptMapChunk,
                    progress
            );

            SurfaceRainHeightPlan plan = surfaceSession.finishPlanning();
            ReadDiagnostics chunkDiagnostics = new ReadDiagnostics();
            Set<ChunkPosition> liquidFailureChunks = new HashSet<>();
            ChunkStreamStats fast = emptyChunkStats();
            if (!plan.chunkPositions().isEmpty()) {
                fast = reader.forEachSurfaceChunkByPositionAdaptive(
                        saveSession,
                        plan.chunkPositions(),
                        chunkDiagnostics,
                        chunk -> consumeSurfaceChunk(
                                chunk,
                                chunkDiagnostics,
                                liquidFailureChunks,
                                surfaceSession::acceptFastChunk
                        ),
                        progress
                );
            }
            List<MapChunkCoordinate> fallbackMapChunks =
                    surfaceSession.fallbackMapChunks();
            List<ChunkPosition> fallbackPositions =
                    fallbackChunkPlanner.plan(metadata, fallbackMapChunks);
            ChunkStreamStats fallback = emptyChunkStats();
            if (!fallbackPositions.isEmpty()) {
                fallback = reader.forEachSurfaceChunkByPositionAdaptive(
                        saveSession,
                        fallbackPositions,
                        chunkDiagnostics,
                        chunk -> consumeSurfaceChunk(
                                chunk,
                                chunkDiagnostics,
                                liquidFailureChunks,
                                surfaceSession::acceptFallbackChunk
                        ),
                        progress
                );
            }
            SurfaceRainHeightScanResult scan = surfaceSession.finish();
            SurfaceRainHeightDiagnosticCounters counters = scan.diagnostics();
            int chunksScanned = Math.addExact(
                    fast.parsedChunks(),
                    fallback.parsedChunks()
            );
            return new ReadSurfaceMapResult(
                    new cartographer.scanner.SurfaceMapScanResult(
                            scan.surface(),
                            registry,
                            chunksScanned,
                            counters.columnsScanned(),
                            counters.emptyColumns(),
                            counters.liquidUnavailableColumns()
                    ),
                    mapDiagnostics,
                    chunkDiagnostics
            );
        }
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
