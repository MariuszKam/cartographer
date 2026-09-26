package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.cache.RenderDataCacheStore;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotPreparationSummary;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.MapRegionStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareWorldSnapshotUseCaseTest {

    @TempDir
    Path root;

    @Test
    void secondRunReusesCompleteTerrainAndSurfaceWithoutSourceChunkReads()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3, 4});

        TestReader reader = new TestReader(List.of(
                mapChunk(0),
                mapChunk(1)
        ));
        WorldMetadataReader metadataReader = new TestMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(root.resolve("cache"));
        PrepareWorldSnapshotUseCase useCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        sessionFactory,
                        cacheStore,
                        new WorldIndexBatchPlanner(16)
                );
        PrepareWorldSnapshotRequest request =
                new PrepareWorldSnapshotRequest(save);

        RecordingProgressReporter progress =
                new RecordingProgressReporter();
        PrepareWorldSnapshotResult first =
                useCase.execute(request, progress);

        assertTrue(first.complete());
        assertTrue(
                progress.monotonic(),
                "world snapshot preparation progress must never move backwards"
        );
        assertEquals(1.0, progress.lastFraction());
        assertTrue(
                progress.doneStages.contains("World snapshot prepared")
        );
        assertEquals(2, first.observedMapChunks());
        assertEquals(2, first.terrainPublished());
        assertEquals(2, first.surfacePublished());
        assertEquals(1, reader.observedScans.get());
        assertEquals(0, reader.exactMapChunkReads.get());
        assertEquals(1, reader.surfaceReads.get());
        assertEquals(1, reader.mapRegionReads.get());
        assertEquals(1, reader.rockReads.get());
        assertEquals(1, reader.resourceReads.get());
        assertTrue(first.mapRegionCoverageComplete());
        assertTrue(first.upperRockCoverageComplete());
        assertEquals(2, first.upperRockPublished());
        assertTrue(first.resourceIndexCoverageComplete());
        var preparationSummary = WorldDataSnapshot.openExisting(
                cacheStore,
                save
        ).orElseThrow().preparationSummaryStore().read().orElseThrow();
        assertEquals(first.revisionHash(), preparationSummary.revisionHash());
        assertEquals(first.observedMapChunks(), preparationSummary.observedMapChunks());
        assertTrue(preparationSummary.complete());

        assertEquals(1, first.resourceBlocksCatalogued());
        assertEquals(0, first.resourceChunkHits());
        assertEquals(4, first.resourceChunksPublished());
        assertEquals(0L, first.resourceOccurrenceColumnsPublished());

        var resourceStore = WorldDataSnapshot.openOrCreate(
                cacheStore,
                save
        ).orElseThrow().resourceIndexStore();
        assertEquals(
                Map.of(
                        2,
                        "game:ore-nativecopper-granite"
                ),
                resourceStore.blockCatalog()
        );
        assertTrue(
                resourceStore.readOccurrences(
                        List.of(
                                new ChunkPosition(0, 0, 0, 0),
                                new ChunkPosition(0, 1, 0, 0),
                                new ChunkPosition(1, 0, 0, 0),
                                new ChunkPosition(1, 1, 0, 0)
                        ),
                        List.of(2)
                ).isEmpty(),
                "registry catalog must not fabricate resource occurrences"
        );

        PrepareWorldSnapshotResult second =
                useCase.execute(request, ProgressReporter.NONE);

        assertTrue(second.complete());
        assertEquals(2, second.observedMapChunks());
        assertEquals(2, second.terrainHits());
        assertEquals(0, second.terrainPublished());
        assertEquals(2, second.surfaceHits());
        assertEquals(0, second.surfacePublished());

        assertEquals(
                1,
                reader.observedScans.get(),
                "completed revision catalog must avoid another source discovery scan"
        );
        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "valid Terrain coverage must avoid repair reads"
        );
        assertEquals(
                1,
                reader.surfaceReads.get(),
                "valid Surface coverage must avoid another server-chunk traversal"
        );
        assertEquals(
                1,
                reader.mapRegionReads.get(),
                "complete mapregion snapshot must avoid another source scan"
        );
        assertEquals(
                1,
                reader.rockReads.get(),
                "complete UPPER_ROCK coverage must avoid another selective source traversal"
        );
        assertEquals(2, second.upperRockHits());
        assertEquals(0, second.upperRockPublished());
        assertEquals(
                1,
                reader.resourceReads.get(),
                "complete resource-index coverage must avoid another source traversal"
        );
        assertEquals(4, second.resourceChunkHits());
        assertEquals(0, second.resourceChunksPublished());
        assertEquals(0L, second.resourceOccurrenceColumnsPublished());
        assertTrue(second.resourceIndexCoverageComplete());


        PrepareMapDataUseCase renderUseCase = new PrepareMapDataUseCase(
                reader,
                sessionFactory,
                Optional.of(cacheStore)
        );
        renderUseCase.execute(
                new PrepareMapDataRequest(
                        save,
                        31,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                        Optional.of(new WorldPosition(32, 0, 16)),
                        SurfaceDataRequirement.RENDER
                ),
                ProgressReporter.NONE
        );

        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "prepared Terrain coverage must avoid source mapchunk lookups"
        );
        assertEquals(
                1,
                reader.surfaceReads.get(),
                "prepared Surface coverage must avoid source server-chunk reads"
        );

        // Move the Surface request into an observed-gap column. A complete
        // mapchunk catalog may skip the Terrain lookup, but it must not invent
        // the absence of server chunks. The first request therefore performs
        // the established authoritative Surface fallback and publishes the
        // resulting complete tile.
        // Center the request inside the unobserved (2,0) mapchunk and
        // choose a radius that covers that whole 32x32 tile. Lazy cache
        // publication is only valid for complete mapchunk Surface tiles;
        // request-clipped tiles must remain non-reusable.
        PrepareMapDataRequest outsidePreparedSurface =
                new PrepareMapDataRequest(
                        save,
                        23,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                        Optional.of(new WorldPosition(80, 0, 16)),
                        SurfaceDataRequirement.RENDER
                );
        renderUseCase.execute(
                outsidePreparedSurface,
                ProgressReporter.NONE
        );

        assertEquals(
                0,
                reader.exactMapChunkReads.get(),
                "complete catalog may skip known-unobserved Terrain mapchunks"
        );
        assertEquals(
                2,
                reader.surfaceReads.get(),
                "Surface outside prepared coverage must retain authoritative fallback"
        );

        // Once that fallback tile has been published for the same revision,
        // the identical render is snapshot-backed as well.
        renderUseCase.execute(
                outsidePreparedSurface,
                ProgressReporter.NONE
        );
        assertEquals(
                2,
                reader.surfaceReads.get(),
                "a lazily published complete Surface tile must be reusable"
        );
    }

    @Test
    void cancellationDuringHeaderPlayerReadIsNotSwallowed()
            throws Exception {
        Path save = root.resolve("header-cancel").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{9, 8, 7});

        TestReader reader = new TestReader(List.of(mapChunk(0)));
        WorldMetadataReader metadataReader = new TestMetadataReader();
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(root.resolve("header-cancel-cache"));
        PrepareWorldSnapshotUseCase useCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        new SaveSessionFactory(
                                new TestConnectionFactory(),
                                reader,
                                metadataReader
                        ),
                        cacheStore,
                        new WorldIndexBatchPlanner(16)
                );

        assertThrows(
                CancellationException.class,
                () -> useCase.execute(
                        new PrepareWorldSnapshotRequest(save),
                        new ProgressReporter() {
                            @Override
                            public void progress(
                                    String stage,
                                    int current,
                                    int total
                            ) {
                                if (stage.contains("Reading PLAYER")) {
                                    throw new CancellationException(
                                            "header cancelled"
                                    );
                                }
                            }
                        }
                )
        );

        WorldDataSnapshot snapshot = WorldDataSnapshot.openExisting(
                cacheStore,
                save
        ).orElseThrow();
        assertTrue(
                snapshot.headerStore().read().isEmpty(),
                "cancelled PLAYER read must not publish a synthetic header"
        );
    }

    @Test
    void cancellationDuringHeaderReadIsNotDowngradedToMissingPlayer()
            throws Exception {
        Path save = root.resolve("header-cancel").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{3, 1, 4});

        TestReader reader = new TestReader(List.of(mapChunk(0))) {
            @Override
            public WorldPosition readPlayerPosition(
                    SaveSession session,
                    ProgressReporter progress
            ) {
                throw new CancellationException("header cancelled");
            }
        };
        WorldMetadataReader metadataReader = new TestMetadataReader();
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(root.resolve("header-cancel-cache"));
        PrepareWorldSnapshotUseCase useCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        new SaveSessionFactory(
                                new TestConnectionFactory(),
                                reader,
                                metadataReader
                        ),
                        cacheStore,
                        new WorldIndexBatchPlanner(16)
                );

        assertThrows(
                CancellationException.class,
                () -> useCase.execute(
                        new PrepareWorldSnapshotRequest(save),
                        ProgressReporter.NONE
                )
        );

        WorldDataSnapshot snapshot = WorldDataSnapshot.openExisting(
                cacheStore,
                save
        ).orElseThrow();
        assertTrue(
                snapshot.headerStore().read().isEmpty(),
                "cancelled Header phase must not publish a fabricated header"
        );
        assertTrue(
                snapshot.preparationSummaryStore().read().isEmpty(),
                "cancelled Header phase must not publish later coverage"
        );
    }

    @Test
    void cancelledRefreshDoesNotDowngradePreviouslyVerifiedLaterCoverage()
            throws Exception {
        Path save = root.resolve("refresh").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{7, 8, 9});

        TestReader reader = new TestReader(List.of(
                mapChunk(0),
                mapChunk(1)
        ));
        WorldMetadataReader metadataReader = new TestMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );
        RenderDataCacheStore cacheStore =
                new RenderDataCacheStore(root.resolve("refresh-cache"));
        PrepareWorldSnapshotUseCase useCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        sessionFactory,
                        cacheStore,
                        new WorldIndexBatchPlanner(16)
                );
        PrepareWorldSnapshotRequest request =
                new PrepareWorldSnapshotRequest(save);

        assertTrue(
                useCase.execute(request, ProgressReporter.NONE).complete()
        );

        assertThrows(
                CancellationException.class,
                () -> useCase.execute(
                        request,
                        new ProgressReporter() {
                            @Override
                            public void progress(
                                    String stage,
                                    int current,
                                    int total
                            ) {
                                if (stage.startsWith(
                                        "[3/6] Surface"
                                )) {
                                    throw new CancellationException(
                                            "test cancellation"
                                    );
                                }
                            }
                        }
                )
        );

        WorldSnapshotPreparationSummary summary =
                WorldDataSnapshot.openExisting(
                        cacheStore,
                        save
                ).orElseThrow()
                        .preparationSummaryStore()
                        .read()
                        .orElseThrow();

        assertTrue(
                summary.complete(),
                "a cancelled refresh must not discard still-valid later-phase evidence"
        );
    }

    private static MapChunk mapChunk(int x) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(heights, 0);
        return new MapChunk(
                new MapChunkCoordinate(x, 0),
                heights,
                heights
        );
    }

    private static class TestReader extends VcdbsReader {
        private final List<MapChunk> mapChunks;
        private final AtomicInteger observedScans = new AtomicInteger();
        private final AtomicInteger exactMapChunkReads = new AtomicInteger();
        private final AtomicInteger surfaceReads = new AtomicInteger();
        private final AtomicInteger mapRegionReads = new AtomicInteger();
        private final AtomicInteger rockReads = new AtomicInteger();
        private final AtomicInteger resourceReads = new AtomicInteger();

        private TestReader(List<MapChunk> mapChunks) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
            this.mapChunks = List.copyOf(mapChunks);
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            // Mimic the real reader's nested lifecycle: finishing one nested
            // stage and starting another must never move snapshot preparation progress
            // backwards within the Header phase.
            progress.start("Reading PLAYER records");
            progress.progress("Reading PLAYER records", 1, 2);
            progress.done("PLAYER records read");
            progress.start("Parsing PLAYER position");
            progress.done("PLAYER position parsed");
            return new WorldPosition(16, 0, 16);
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    0, new BlockInfo(0, "game:air"),
                    1, new BlockInfo(1, "game:rock-granite"),
                    2, new BlockInfo(
                            2,
                            "game:ore-nativecopper-granite"
                    )
            );
        }

        @Override
        public MapChunkStreamStats forEachObservedMapChunk(
                SaveSession session,
                ReadDiagnostics diagnostics,
                Consumer<MapChunkCoordinate> observedCoordinateConsumer,
                Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            observedScans.incrementAndGet();
            mapChunks.forEach(mapChunk -> {
                observedCoordinateConsumer.accept(mapChunk.coordinate());
                diagnostics.recordParsed();
                consumer.accept(mapChunk);
            });
            return new MapChunkStreamStats(
                    mapChunks.size(),
                    mapChunks.isEmpty() ? 0 : 1,
                    mapChunks.size(),
                    mapChunks.size(),
                    0,
                    0
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            exactMapChunkReads.incrementAndGet();
            List<MapChunkCoordinate> requested = List.copyOf(coordinates);
            List<MapChunk> matching = mapChunks.stream()
                    .filter(mapChunk -> requested.contains(mapChunk.coordinate()))
                    .toList();
            matching.forEach(mapChunk -> {
                diagnostics.recordParsed();
                consumer.accept(mapChunk);
            });
            return new MapChunkStreamStats(
                    requested.size(),
                    requested.isEmpty() ? 0 : 1,
                    matching.size(),
                    matching.size(),
                    0,
                    0
            );
        }

        @Override
        public MapRegionStreamStats forEachObservedMapRegion(
                SaveSession session,
                ReadDiagnostics diagnostics,
                Consumer<ServerMapRegion> consumer,
                ProgressReporter progress
        ) {
            mapRegionReads.incrementAndGet();
            return new MapRegionStreamStats(0, 0, 0, 0, 0, 0L);
        }

        @Override
        public SelectiveChunkStreamStats
        forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            boolean resourceRead = java.util.Arrays.stream(wantedBlockIds)
                    .anyMatch(blockId -> blockId == 2);
            if (resourceRead) {
                resourceReads.incrementAndGet();
            } else {
                rockReads.incrementAndGet();
            }
            for (ChunkPosition position : positions) {
                consumer.accept(
                        SelectiveChunkVisit.paletteRejected(position)
                );
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    positions.size(),
                    positions.size(),
                    positions.size(),
                    0,
                    0,
                    0L
            );
        }

        @Override
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            surfaceReads.incrementAndGet();
            for (ChunkPosition position : positions) {
                ParsedChunk chunk = solidChunk(position);
                diagnostics.recordParsed();
                consumer.accept(chunk);
            }
            return new ChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    positions.size(),
                    positions.size(),
                    0,
                    0
            );
        }

        private ParsedChunk solidChunk(ChunkPosition position) {
            int cells = ChunkCoordinate.SIZE_BLOCKS
                    * ChunkCoordinate.SIZE_BLOCKS
                    * ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[cells];
            int[] liquids = new int[cells];
            Arrays.fill(blocks, 1);
            return cartographer.model.ParsedChunkFixtures.create(
                    new ChunkCoordinate(
                            position.x(),
                            position.y(),
                            position.z()
                    ),
                    position.y() * ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    ChunkCoordinate.SIZE_BLOCKS,
                    blocks,
                    liquids,
                    2
            );
        }
    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(Connection connection) {
            return new WorldMetadata(96, 64, 32);
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return false;
                        }
                        if (method.getReturnType() == boolean.class) {
                            return false;
                        }
                        if (method.getReturnType() == int.class) {
                            return 0;
                        }
                        if (method.getReturnType() == long.class) {
                            return 0L;
                        }
                        return null;
                    }
            );
        }
    }
    private static final class RecordingProgressReporter
            implements ProgressReporter {
        private final List<Double> fractions = new ArrayList<>();
        private final List<String> doneStages = new ArrayList<>();

        @Override
        public void progress(
                String stage,
                int current,
                int total
        ) {
            if (total > 0) {
                fractions.add(
                        Math.clamp(
                                current / (double) total,
                                0.0,
                                1.0
                        )
                );
            }
        }

        @Override
        public void done(String stage) {
            doneStages.add(stage);
        }

        private boolean monotonic() {
            double previous = -1.0;
            for (double fraction : fractions) {
                if (fraction < previous) {
                    return false;
                }
                previous = fraction;
            }
            return true;
        }

        private double lastFraction() {
            if (fractions.isEmpty()) {
                throw new AssertionError("no numeric progress was reported");
            }
            return fractions.get(fractions.size() - 1);
        }
    }

}
