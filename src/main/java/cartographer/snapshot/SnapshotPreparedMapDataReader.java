package cartographer.snapshot;

import cartographer.application.MapChunkPositionPlanner;
import cartographer.application.MapChunkRenderWindowPlanner;
import cartographer.application.PrepareMapDataRequest;
import cartographer.application.PreparedMapData;
import cartographer.application.PreparedSurfaceData;
import cartographer.application.ProgressReporter;
import cartographer.application.RenderDataCacheReport;
import cartographer.application.SurfaceDataRequirement;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.SurfaceCacheTile;
import cartographer.perf.SurfaceTileLookup;
import cartographer.perf.TerrainTileLookup;
import cartographer.perf.WorldDataSnapshot;
import cartographer.perf.WorldIndexCatalogStore;
import cartographer.perf.WorldSnapshotHeader;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.RenderOptions;
import cartographer.render.RenderSamplingPlan;
import cartographer.render.SurfaceRenderData;
import cartographer.save.ReadDiagnostics;
import cartographer.scanner.SurfaceDiagnosticsSummary;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRainHeightDiagnosticCounters;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceStreamingSession;
import cartographer.scanner.SurfaceTileLayout;

import java.util.List;
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

            Optional<SurfaceRead> surfaceRead;
            if (request.surfaceDataRequirement()
                    == SurfaceDataRequirement.NONE) {
                surfaceRead = Optional.of(SurfaceRead.empty(
                        PreparedSurfaceData.none(center, options)
                ));
            } else if (request.surfaceDataRequirement()
                    == SurfaceDataRequirement.RENDER) {
                surfaceRead = renderSurface(
                        snapshot,
                        metadata,
                        header,
                        center,
                        request.radius(),
                        options
                );
            } else {
                surfaceRead = analysisSurface(
                        snapshot,
                        metadata,
                        header,
                        center,
                        request.radius(),
                        options
                );
            }
            if (surfaceRead.isEmpty()) {
                return Optional.empty();
            }

            List<MapChunkCoordinate> terrainCoordinates =
                    renderWindowPlanner.plan(
                            metadata,
                            center,
                            request.radius()
                    );
            Optional<TerrainRead> terrainRead = terrain(
                    snapshot,
                    terrainCoordinates,
                    center,
                    options,
                    surfaceRead.orElseThrow().data().renderData(),
                    progress
            );
            if (terrainRead.isEmpty()) {
                return Optional.empty();
            }

            MapTerrainPreparation terrain =
                    terrainRead.orElseThrow().terrain();

            RenderDataCacheReport report = new RenderDataCacheReport(
                    true,
                    new RenderDataCacheReport.ArtifactStats(
                            terrainCoordinates.size(),
                            terrainRead.orElseThrow().hits(),
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
                    surfaceRead.orElseThrow().data(),
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
            List<MapChunkCoordinate> coordinates,
            WorldPosition center,
            RenderOptions options,
            SurfaceRenderData surfaceRenderData,
            ProgressReporter progress
    ) {
        progress.start("Composing Terrain from world snapshot");
        MapTerrainPreparation.Builder builder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        coordinates.size(),
                        progress,
                        surfaceRenderData
                );
        if (coordinates.isEmpty()) {
            return Optional.of(new TerrainRead(
                    builder.finish(),
                    0,
                    0
            ));
        }

        WorldIndexCatalogStore catalog = snapshot.indexCatalogStore();
        if (!catalog.mapChunkScanComplete()) {
            return Optional.empty();
        }

        Set<MapChunkCoordinate> observed =
                catalog.observedAmong(coordinates);
        int[] hits = {0};
        int[] knownAbsent = {0};
        boolean[] complete = {true};

        snapshot.terrainStore().forEachLookup(
                coordinates,
                (coordinate, lookup) -> {
                    if (lookup.status()
                            == TerrainTileLookup.Status.HIT) {
                        builder.accept(lookup.tile());
                        hits[0]++;
                        return true;
                    }
                    if (lookup.status()
                            == TerrainTileLookup.Status.MISS
                            && !observed.contains(coordinate)) {
                        knownAbsent[0]++;
                        return true;
                    }
                    complete[0] = false;
                    return false;
                }
        );

        if (!complete[0]) {
            return Optional.empty();
        }
        return Optional.of(new TerrainRead(
                builder.finish(),
                hits[0],
                knownAbsent[0]
        ));
    }

    private Optional<SurfaceRead> analysisSurface(
            WorldDataSnapshot snapshot,
            WorldMetadata metadata,
            WorldSnapshotHeader header,
            WorldPosition center,
            int radius,
            RenderOptions options
    ) {
        int centerX = checkedRound(center.x());
        int centerZ = checkedRound(center.z());
        List<MapChunkCoordinate> coordinates = surfacePlanner.plan(
                metadata,
                centerX,
                centerZ,
                radius
        );
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
        int[] hits = {0};
        boolean[] complete = {true};

        snapshot.surfaceStore().forEachLookup(
                coordinates,
                (coordinate, lookup) -> {
                    if (lookup.status() != SurfaceTileLookup.Status.HIT
                            || !lookup.tile().matchesWorld(metadata)) {
                        // A complete mapchunk catalog is deliberately not
                        // enough to infer server-chunk absence for Surface.
                        complete[0] = false;
                        return false;
                    }
                    session.acceptCachedTile(lookup.tile());
                    hits[0]++;
                    return true;
                }
        );

        if (!complete[0]) {
            return Optional.empty();
        }

        SurfaceRainHeightScanResult result = session.finish();
        SurfaceRainHeightDiagnosticCounters diagnostics =
                result.diagnostics();
        SurfaceMapScanResult exact = new SurfaceMapScanResult(
                result.surface(),
                header.blockRegistry(),
                0,
                diagnostics.columnsScanned(),
                diagnostics.emptyColumns(),
                diagnostics.liquidUnavailableColumns()
        );
        return Optional.of(new SurfaceRead(
                PreparedSurfaceData.fromExact(
                        exact,
                        center,
                        options,
                        SurfaceDataRequirement.ANALYSIS
                ),
                coordinates.size(),
                hits[0]
        ));
    }

    private Optional<SurfaceRead> renderSurface(
            WorldDataSnapshot snapshot,
            WorldMetadata metadata,
            WorldSnapshotHeader header,
            WorldPosition center,
            int radius,
            RenderOptions options
    ) {
        int centerX = checkedRound(center.x());
        int centerZ = checkedRound(center.z());
        List<MapChunkCoordinate> coordinates = surfacePlanner.plan(
                metadata,
                centerX,
                centerZ,
                radius
        );
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                centerX,
                centerZ,
                radius,
                metadata
        );
        SurfaceRenderData.Builder renderBuilder =
                SurfaceRenderData.builder(
                        RenderSamplingPlan.from(center, options),
                        layout
                );
        SurfaceDiagnosticsSummary.Builder diagnostics =
                SurfaceDiagnosticsSummary.builder(
                        header.blockRegistry()
                );
        int[] hits = {0};
        boolean[] complete = {true};

        snapshot.surfaceStore().forEachLookup(
                coordinates,
                (coordinate, lookup) -> {
                    if (lookup.status() != SurfaceTileLookup.Status.HIT
                            || !lookup.tile().matchesWorld(metadata)) {
                        // Surface snapshot coverage must be complete. Missing
                        // derived data never proves source absence.
                        complete[0] = false;
                        return false;
                    }

                    SurfaceCacheTile tile = lookup.tile();
                    int tileX = tile.coordinate().x();
                    int tileZ = tile.coordinate().z();
                    for (int localZ = 0;
                         localZ < tile.height();
                         localZ++) {
                        int worldZ = layout.worldZForTileLocal(
                                tileZ,
                                localZ
                        );
                        for (int localX = 0;
                             localX < tile.width();
                             localX++) {
                            int worldX = layout.worldXForTileLocal(
                                    tileX,
                                    localX
                            );
                            if (!layout.isActive(worldX, worldZ)) {
                                continue;
                            }

                            int cellIndex =
                                    localZ * tile.width() + localX;
                            byte state = tile.stateAtIndex(cellIndex);
                            if (!tile.fallbackMode()
                                    && (state
                                    & SurfaceCacheTile.CONSIDERED) != 0) {
                                diagnostics.addColumnsScanned(1);
                            }
                            if ((state & SurfaceCacheTile.RESOLVED) == 0) {
                                continue;
                            }

                            SurfaceClass surfaceClass =
                                    SurfaceClassCode.decode(
                                            tile.surfaceClassCodeAtIndex(
                                                    cellIndex
                                            )
                                    );
                            int blockId =
                                    tile.blockIdAtIndex(cellIndex);
                            diagnostics.acceptResolved(
                                    blockId,
                                    surfaceClass
                            );
                            renderBuilder.acceptResolved(
                                    worldX,
                                    worldZ,
                                    surfaceClass
                            );
                        }
                    }

                    if (tile.fallbackMode()) {
                        diagnostics.addColumnsScanned(
                                tile.diagnosticColumnsScanned()
                        );
                        diagnostics.addEmptyColumns(
                                tile.diagnosticEmptyColumns()
                        );
                        diagnostics.addLiquidUnavailableColumns(
                                tile.diagnosticLiquidUnavailableColumns()
                        );
                    }
                    hits[0]++;
                    return true;
                }
        );

        if (!complete[0]) {
            return Optional.empty();
        }

        return Optional.of(new SurfaceRead(
                PreparedSurfaceData.renderOnly(
                        renderBuilder.finish(),
                        diagnostics.build()
                ),
                coordinates.size(),
                hits[0]
        ));
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
            MapTerrainPreparation terrain,
            int hits,
            int knownAbsent
    ) {
        private TerrainRead {
            Objects.requireNonNull(
                    terrain,
                    "terrain preparation is required"
            );
            if (hits < 0 || knownAbsent < 0) {
                throw new IllegalArgumentException(
                        "terrain counters cannot be negative"
                );
            }
        }
    }

    private record SurfaceRead(
            PreparedSurfaceData data,
            int requested,
            int hits
    ) {
        private SurfaceRead {
            Objects.requireNonNull(data, "prepared Surface data is required");
            if (requested < 0 || hits < 0 || hits > requested) {
                throw new IllegalArgumentException(
                        "invalid Surface snapshot counters"
                );
            }
        }

        static SurfaceRead empty(PreparedSurfaceData data) {
            return new SurfaceRead(data, 0, 0);
        }
    }
}
