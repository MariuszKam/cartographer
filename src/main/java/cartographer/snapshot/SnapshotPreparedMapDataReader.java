package cartographer.snapshot;

import cartographer.application.MapChunkPositionPlanner;
import cartographer.application.MapChunkRenderWindowPlanner;
import cartographer.application.PrepareMapDataRequest;
import cartographer.application.PreparedMapData;
import cartographer.application.ProgressReporter;
import cartographer.application.RenderDataCacheReport;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.SurfaceCacheTile;
import cartographer.perf.SurfaceTileLookup;
import cartographer.perf.TerrainHeightTile;
import cartographer.perf.TerrainTileLookup;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldIndexCatalogStore;
import cartographer.perf.WorldSnapshotHeader;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderOptions;
import cartographer.save.ReadDiagnostics;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * PF-2.6 source-free consumer for prepared Terrain/Surface snapshot data.
 *
 * <p>The reader succeeds only when every datum required by the request is
 * proven compatible in the current revision namespace. A miss, corrupt row,
 * missing header/player or incomplete authoritative catalog returns
 * {@link Optional#empty()} so the caller can use the established source path.
 * No source JDBC connection is opened here.</p>
 */
public final class SnapshotPreparedMapDataReader {
    private final RenderDataCacheStore cacheStore;
    private final MapChunkRenderWindowPlanner renderWindowPlanner =
            new MapChunkRenderWindowPlanner();
    private final MapChunkPositionPlanner surfacePlanner =
            new MapChunkPositionPlanner();

    public SnapshotPreparedMapDataReader(RenderDataCacheStore cacheStore) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public Optional<PreparedMapData> read(
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        try {
            Optional<WorldDataSnapshot> snapshotOptional =
                    WorldDataSnapshot.openOrCreate(
                            cacheStore,
                            request.savePath()
                    );
            if (snapshotOptional.isEmpty()) {
                return Optional.empty();
            }
            WorldDataSnapshot snapshot = snapshotOptional.orElseThrow();
            Optional<WorldSnapshotHeader> headerOptional =
                    snapshot.headerStore().read();
            if (headerOptional.isEmpty()) {
                return Optional.empty();
            }
            WorldSnapshotHeader header = headerOptional.orElseThrow();
            if (header.player().isEmpty()) {
                // PreparedMapData carries PLAYER independently of whether the
                // current request explicitly centers on it.
                return Optional.empty();
            }

            WorldMetadata metadata = header.metadata();
            WorldPosition player = header.player().orElseThrow();
            WorldPosition center = request.center().orElse(player);
            RenderOptions options = new RenderOptions(
                    request.radius(),
                    request.pixelsPerBlock(),
                    request.style(),
                    request.layers()
            );

            List<MapChunkCoordinate> terrainCoordinates =
                    renderWindowPlanner.plan(
                            metadata,
                            center,
                            request.radius()
                    );
            Optional<TerrainRead> terrainRead = terrain(
                    snapshot,
                    terrainCoordinates
            );
            if (terrainRead.isEmpty()) {
                return Optional.empty();
            }

            progress.start("Composing Terrain from world snapshot");
            MapTerrainPreparation.Builder terrainBuilder =
                    MapTerrainPreparation.builder(
                            center,
                            options,
                            terrainCoordinates.size(),
                            progress
                    );
            for (MapChunkCoordinate coordinate : terrainCoordinates) {
                TerrainHeightTile tile =
                        terrainRead.orElseThrow().hits().get(coordinate);
                if (tile != null) {
                    terrainBuilder.accept(tile);
                }
            }
            MapTerrainPreparation terrain = terrainBuilder.finish();

            Optional<SurfaceRead> surfaceRead = request.requireSurfaceData()
                    ? surface(
                    snapshot,
                    metadata,
                    header,
                    center,
                    request.radius()
            )
                    : Optional.of(SurfaceRead.empty(
                    emptySurface(
                            metadata,
                            center,
                            request.radius()
                    )
            ));
            if (surfaceRead.isEmpty()) {
                return Optional.empty();
            }

            RenderDataCacheReport report = new RenderDataCacheReport(
                    true,
                    new RenderDataCacheReport.ArtifactStats(
                            terrainCoordinates.size(),
                            terrainRead.orElseThrow().hits().size(),
                            terrainRead.orElseThrow().knownAbsent(),
                            0,
                            0,
                            0,
                            0,
                            0
                    ),
                    new RenderDataCacheReport.ArtifactStats(
                            surfaceRead.orElseThrow().requested(),
                            surfaceRead.orElseThrow().hits(),
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                    ),
                    List.of(
                            "PF-2.6 snapshot-backed warm path",
                            "source SaveSession not opened"
                    )
            );

            progress.done("Prepared map data from world snapshot");
            return Optional.of(new PreparedMapData(
                    metadata,
                    player,
                    center,
                    options,
                    terrain,
                    surfaceRead.orElseThrow().result(),
                    header.blockRegistry(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    report
            ));
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            // Derived data is never authoritative. Any unexpected derived
            // state is treated as a snapshot miss and delegated to source.
            return Optional.empty();
        }
    }

    private Optional<TerrainRead> terrain(
            WorldDataSnapshot snapshot,
            List<MapChunkCoordinate> coordinates
    ) {
        if (coordinates.isEmpty()) {
            return Optional.of(new TerrainRead(Map.of(), 0));
        }

        WorldIndexCatalogStore catalog = snapshot.indexCatalogStore();
        if (!catalog.mapChunkScanComplete()) {
            return Optional.empty();
        }

        Set<MapChunkCoordinate> observed =
                catalog.observedAmong(coordinates);
        Map<MapChunkCoordinate, TerrainTileLookup> lookups =
                snapshot.terrainStore().read(coordinates);
        LinkedHashMap<MapChunkCoordinate, TerrainHeightTile> hits =
                new LinkedHashMap<>();
        int knownAbsent = 0;

        for (MapChunkCoordinate coordinate : coordinates) {
            TerrainTileLookup lookup = lookups.getOrDefault(
                    coordinate,
                    TerrainTileLookup.miss()
            );
            if (lookup.status() == TerrainTileLookup.Status.HIT) {
                hits.put(coordinate, lookup.tile());
                continue;
            }
            if (lookup.status() == TerrainTileLookup.Status.MISS
                    && !observed.contains(coordinate)) {
                knownAbsent++;
                continue;
            }
            return Optional.empty();
        }
        return Optional.of(new TerrainRead(
                Map.copyOf(hits),
                knownAbsent
        ));
    }

    private Optional<SurfaceRead> surface(
            WorldDataSnapshot snapshot,
            WorldMetadata metadata,
            WorldSnapshotHeader header,
            WorldPosition center,
            int radius
    ) {
        int centerX = checkedRound(center.x());
        int centerZ = checkedRound(center.z());
        List<MapChunkCoordinate> coordinates = surfacePlanner.plan(
                metadata,
                centerX,
                centerZ,
                radius
        );
        Map<MapChunkCoordinate, SurfaceTileLookup> lookups =
                snapshot.surfaceStore().read(coordinates);
        List<SurfaceCacheTile> hits = new ArrayList<>(
                coordinates.size()
        );

        for (MapChunkCoordinate coordinate : coordinates) {
            SurfaceTileLookup lookup = lookups.getOrDefault(
                    coordinate,
                    SurfaceTileLookup.miss()
            );
            if (lookup.status() != SurfaceTileLookup.Status.HIT
                    || !lookup.tile().matchesWorld(metadata)) {
                // A complete mapchunk catalog is deliberately not enough to
                // infer server-chunk absence for Surface.
                return Optional.empty();
            }
            hits.add(lookup.tile());
        }

        progresslessSurfaceValidation(hits);

        SurfaceStreamingSession session = SurfaceStreamingSession.begin(
                metadata,
                centerX,
                centerZ,
                radius,
                List.of(),
                header.blockRegistry(),
                true,
                true
        );
        session.finishPlanning();
        for (SurfaceCacheTile tile : hits) {
            session.acceptCachedTile(
                    tile.coordinate(),
                    tile.width(),
                    tile.height(),
                    tile.state(),
                    tile.surfaceY(),
                    tile.blockIds(),
                    tile.liquidBlockIds(),
                    tile.surfaceClassCodes(),
                    tile.sourceMode()
                            == SurfaceCacheTile.SourceMode.FALLBACK,
                    tile.diagnosticColumnsScanned(),
                    tile.diagnosticEmptyColumns(),
                    tile.diagnosticLiquidUnavailableColumns()
            );
        }

        SurfaceRainHeightScanResult result = session.finish();
        SurfaceRainHeightDiagnosticCounters diagnostics =
                result.diagnostics();
        return Optional.of(new SurfaceRead(
                new SurfaceMapScanResult(
                        result.surface(),
                        header.blockRegistry(),
                        0,
                        diagnostics.columnsScanned(),
                        diagnostics.emptyColumns(),
                        diagnostics.liquidUnavailableColumns()
                ),
                coordinates.size(),
                hits.size()
        ));
    }

    /**
     * Makes the full-tile requirement explicit before composition; no
     * inference or source repair is allowed on this source-free path.
     */
    private void progresslessSurfaceValidation(
            List<SurfaceCacheTile> tiles
    ) {
        for (SurfaceCacheTile tile : tiles) {
            Objects.requireNonNull(tile, "Surface snapshot tile is required");
        }
    }

    private SurfaceMapScanResult emptySurface(
            WorldMetadata metadata,
            WorldPosition center,
            int radius
    ) {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                center.x(),
                center.z(),
                radius,
                metadata
        );
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(layout);
        return new SurfaceMapScanResult(
                accumulator.finish(),
                Map.of(),
                0,
                0,
                0,
                0
        );
    }

    private int checkedRound(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "world center must be finite"
            );
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world center is outside the supported block range"
            );
        }
        return (int) rounded;
    }

    private record TerrainRead(
            Map<MapChunkCoordinate, TerrainHeightTile> hits,
            int knownAbsent
    ) {
        private TerrainRead {
            hits = Map.copyOf(Objects.requireNonNull(
                    hits,
                    "terrain hits are required"
            ));
            if (knownAbsent < 0) {
                throw new IllegalArgumentException(
                        "knownAbsent cannot be negative"
                );
            }
        }
    }

    private record SurfaceRead(
            SurfaceMapScanResult result,
            int requested,
            int hits
    ) {
        private SurfaceRead {
            Objects.requireNonNull(result, "surface result is required");
            if (requested < 0 || hits < 0 || hits > requested) {
                throw new IllegalArgumentException(
                        "invalid Surface snapshot counters"
                );
            }
        }

        static SurfaceRead empty(SurfaceMapScanResult result) {
            return new SurfaceRead(result, 0, 0);
        }
    }
}
