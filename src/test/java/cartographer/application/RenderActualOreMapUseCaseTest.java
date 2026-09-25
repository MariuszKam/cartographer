package cartographer.application;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.spatial.OreChunkPositionPlanner;
import cartographer.progress.ProgressReporter;
import cartographer.testing.IntegrationTest;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.cache.RenderDataCacheRevision;
import cartographer.cache.RenderDataCacheStore;
import cartographer.index.ResourceChunkIndexEntry;
import cartographer.index.ResourceOccurrence;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileStore;
import cartographer.cache.SurfaceTileStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cartographer.testing.ImageAssertions.assertImageEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class RenderActualOreMapUseCaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void renderRequestRequiresNonNullOptionalReferences() {
        assertEquals(
                Optional.empty(),
                new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"),
                        1,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(),
                        Optional.empty(),
                        ActualBlockYFilter.unbounded(),
                        Optional.empty()
                ).oreMatch()
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), null, ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.empty(), ActualBlockYFilter.unbounded(), null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderActualOreMapRequest(
                        Path.of("save.vcdbs"), 1, 1, RenderStyle.SIMPLE,
                        Set.of(), Optional.of(" "), ActualBlockYFilter.unbounded(), Optional.empty()
                )
        );
    }

    @Test
    void renderResultRequiresNonNullActualOreMapOptional() {
        assertThrows(
                NullPointerException.class,
                () -> new RenderActualOreMapResult(
                        null, null, null, null, null, null, null,
                        null, null, null, null, 0, List.of(),
                        RenderDataCacheReport.disabled("test"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
    }

    @Test
    void usesSelectiveReaderForOreAndStreamsMatchingChunk() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapResult result = execute(reader);

        assertEquals(1, reader.adaptiveSelectiveCalls);
        assertEquals(1, reader.selectiveCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertArrayEquals(new int[]{1}, reader.lastWantedBlockIds);
        assertFalse(reader.lastPositions.isEmpty());
        assertEquals(1, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.preparedMapData().isPresent());
        assertTrue(result.decorationState().isPresent());
        assertFalse(result.renderDataCacheReport().enabled());
        assertEquals(64, result.geometry().imageWidth());
        assertEquals(48.0, result.geometry().worldMinX());
        assertEquals(48.0, result.geometry().worldMinZ());
        assertEquals(80.0, result.geometry().worldMaxXExclusive());
        assertEquals(80.0, result.geometry().worldMaxZExclusive());
        assertEquals(
                ActualBlockMatchMode.ORE_CODE,
                result.actualOreOverlays().getFirst().spec().matchMode()
        );
    }

    @Test
    void retainedOreRenderSkipsBasePreparationButStillScansOreAuthoritatively() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("retained-ore-home.properties"),
                temporaryDirectory.resolve("retained-ore-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("retained-ore-save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                List.of(new ActualOreOverlaySpec(
                        "Cassiterite",
                        "cassiterite",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );

        RenderActualOreMapResult first = useCase.execute(request);
        int baseMapChunkCalls = reader.directMapChunkCalls;
        int baseAdaptiveExactCalls = reader.adaptiveExactChunkCalls;
        int baseSelectiveCalls = reader.adaptiveSelectiveCalls;

        RenderActualOreMapResult retained = useCase.executeRetained(
                request,
                request.savePath(),
                first.preparedMapData().orElseThrow(),
                first.decorationState().orElseThrow(),
                first.mapRegionOverlayState(),
                ProgressReporter.NONE
        );

        assertEquals(baseMapChunkCalls, reader.directMapChunkCalls);
        assertEquals(baseAdaptiveExactCalls, reader.adaptiveExactChunkCalls);
        assertEquals(baseSelectiveCalls + 1, reader.adaptiveSelectiveCalls);
        assertEquals(0, retained.mapChunkDiagnostics().parsed());
        assertEquals(0, retained.mapChunkDiagnostics().skipped());
        assertEquals(0, retained.mapChunkDiagnostics().failed());
        assertEquals(0, retained.chunkDiagnostics().parsed());
        assertEquals(0, retained.chunkDiagnostics().skipped());
        assertEquals(0, retained.chunkDiagnostics().failed());
        assertEquals(1, retained.actualOreOverlays().getFirst().map().matchingBlocks());
        assertImageEquals(first.image(), retained.image());
        assertTrue(retained.renderDataCacheReport().notes().stream()
                .anyMatch(note -> note.contains("retained PreparedMapData reused")));
    }

    @Test
    void retainedOreRejectsCrossSaveReuseBeforeAdditionalReads() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("cross-save-home.properties"),
                temporaryDirectory.resolve("cross-save-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("cross-save-a.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                List.of(new ActualOreOverlaySpec(
                        "Cassiterite",
                        "cassiterite",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );
        RenderActualOreMapResult first = useCase.execute(request);
        int mapChunkCalls = reader.directMapChunkCalls;
        int selectiveCalls = reader.adaptiveSelectiveCalls;

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.executeRetained(
                        request,
                        temporaryDirectory.resolve("cross-save-b.vcdbs"),
                        first.preparedMapData().orElseThrow(),
                        first.decorationState().orElseThrow(),
                        first.mapRegionOverlayState(),
                        ProgressReporter.NONE
                )
        );

        assertEquals(mapChunkCalls, reader.directMapChunkCalls);
        assertEquals(selectiveCalls, reader.adaptiveSelectiveCalls);
    }

    @Test
    void retainedEnvironmentOverlayAvoidsSecondMapRegionReadAndKeepsImageParity() {
        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("retained-region-home.properties"),
                temporaryDirectory.resolve("retained-region-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("retained-region-save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT),
                Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                List.of()
        );

        RenderActualOreMapResult first = useCase.execute(request);
        int mapRegionCalls = reader.sessionMapRegionCalls;
        RenderActualOreMapResult retained = useCase.executeRetained(
                request,
                request.savePath(),
                first.preparedMapData().orElseThrow(),
                first.decorationState().orElseThrow(),
                first.mapRegionOverlayState(),
                ProgressReporter.NONE
        );

        assertEquals(mapRegionCalls, reader.sessionMapRegionCalls);
        assertEquals(0, retained.mapRegionDiagnostics().parsed());
        assertTrue(retained.mapRegionOverlayState().orElseThrow().environmentPrepared());
        assertImageEquals(first.image(), retained.image());
    }

    @Test
    void completeResourceSnapshotSkipsSelectiveOreSourceTraversal()
            throws Exception {
        Path savePath = temporaryDirectory.resolve(
                "pf26-resource-save.vcdbs"
        );
        Files.write(savePath, new byte[]{1, 2, 3});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("pf26-resource-cache")
        );
        WorldMetadata metadata = new WorldMetadata(128, 256, 128);
        FakeReader reader = new FakeReader(Map.of(
                1,
                new BlockInfo(1, "ore-cassiterite-granite")
        ));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(
                        cacheStore,
                        savePath
                ).orElseThrow();
        snapshot.resourceIndexStore().publishBlockCatalog(List.of(
                new BlockInfo(1, "ore-cassiterite-granite")
        ));

        List<ChunkPosition> positions = new OreChunkPositionPlanner().plan(
                metadata,
                64,
                64,
                16,
                ActualBlockYFilter.unbounded()
        );
        List<ResourceChunkIndexEntry> entries =
                new ArrayList<>();
        for (ChunkPosition position : positions) {
            if (position.x() == 2
                    && position.y() == 0
                    && position.z() == 2) {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of(new ResourceOccurrence(
                                position,
                                1,
                                0,
                                0,
                                1L << 5
                        ))
                ));
            } else {
                entries.add(ResourceChunkIndexEntry.available(
                        position,
                        List.of()
                ));
            }
        }
        snapshot.resourceIndexStore().publish(entries);

        RenderActualOreMapUseCase useCase = useCase(
                reader,
                metadata,
                temporaryDirectory.resolve("pf26-resource-home.properties"),
                temporaryDirectory.resolve("pf26-resource-markers.csv"),
                cacheStore
        );
        RenderActualOreMapResult result = useCase.execute(
                new RenderActualOreMapRequest(
                        savePath,
                        16,
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        Set.of(RenderLayer.TERRAIN),
                        Optional.of("cassiterite"),
                        ActualBlockYFilter.unbounded(),
                        Optional.of(new WorldPosition(64, 64, 64)),
                        List.of(new ActualOreOverlaySpec(
                                "Cassiterite",
                                "cassiterite",
                                Color.ORANGE,
                                ActualBlockMatchMode.ORE_CODE
                        ))
                )
        );

        assertEquals(0, reader.adaptiveSelectiveCalls);
        assertEquals(
                1,
                result.actualOreOverlays()
                        .getFirst()
                        .map()
                        .matchingBlocks()
        );
        assertEquals(
                5,
                result.actualOreOverlays()
                        .getFirst()
                        .map()
                        .minMatchedY()
        );
    }

    @Test
    void healthyCompleteMapregionSnapshotSkipsSourceMapregionRead()
            throws Exception {
        Path savePath = temporaryDirectory.resolve(
                "pf26-mapregion-save.vcdbs"
        );
        Files.write(savePath, new byte[]{4, 5, 6});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("pf26-mapregion-cache")
        );
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(
                        cacheStore,
                        savePath
                ).orElseThrow();
        snapshot.mapRegionStore().markScanComplete();

        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("pf26-region-home.properties"),
                temporaryDirectory.resolve("pf26-region-markers.csv"),
                cacheStore
        );

        RenderActualOreMapResult result = useCase.execute(
                new RenderActualOreMapRequest(
                        savePath,
                        16,
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        Set.of(
                                RenderLayer.TERRAIN,
                                RenderLayer.ENVIRONMENT
                        ),
                        Optional.empty(),
                        ActualBlockYFilter.unbounded(),
                        Optional.of(new WorldPosition(64, 64, 64)),
                        List.of()
                )
        );

        assertEquals(0, reader.sessionMapRegionCalls);
        assertTrue(
                result.mapRegionOverlayState()
                        .orElseThrow()
                        .environmentPrepared()
        );
    }

    @Test
    void avoidsSelectiveReaderWhenRegistryHasNoMatchingIds() {
        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapResult result = execute(reader);

        assertEquals(0, reader.selectiveCalls);
        assertEquals(0, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.actualOreOverlays().getFirst().map().cells().isEmpty());
    }

    @Test
    void twoOverlaysPreserveOrderAndUseOneSelectiveLookup() {
        FakeReader reader = new FakeReader(Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "ore-nativecopper-granite")
        ));
        RenderActualOreMapResult result = execute(
                reader,
                List.of(
                        new ActualOreOverlaySpec("Cassiterite", "cassiterite", Color.ORANGE, ActualBlockMatchMode.ORE_CODE),
                        new ActualOreOverlaySpec("Copper", "nativecopper", Color.RED, ActualBlockMatchMode.ORE_CODE)
                )
        );

        assertEquals(1, reader.selectiveCalls);
        assertEquals("Cassiterite", result.actualOreOverlays().get(0).spec().displayName());
        assertEquals("Copper", result.actualOreOverlays().get(1).spec().displayName());
    }

    @Test
    void genericSubstringMatchesNonOreCodeButOreCodeDoesNot() {
        FakeReader genericReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult generic = execute(
                genericReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.GENERIC_SUBSTRING
                ))
        );
        FakeReader oreReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult ore = execute(
                oreReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );

        assertEquals(1, generic.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(0, oreReader.selectiveCalls);
        assertEquals(0, ore.actualOreOverlays().getFirst().map().matchingBlocks());
    }

    @Test
    void surfaceDisabledDoesNotReadSurfaceChunks() {
        FakeReader reader = new FakeReader(Map.of());

        execute(reader, List.of(), Set.of(RenderLayer.TERRAIN), 64, 64, 16);

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(0, reader.exactChunkCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(1, reader.registryCalls);
    }

    @Test
    void malformedFinalManifestDisablesCacheAndUsesSource() throws Exception {
        Path savePath = temporaryDirectory.resolve("malformed-render-data-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("malformed-render-data-cache")
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        Files.writeString(cacheStore.manifestPath(revision), "not-a-manifest\n");

        FakeReader reader = surfaceReader(true);
        RenderActualOreMapResult result = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("malformed-home.properties"),
                temporaryDirectory.resolve("malformed-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertFalse(result.renderDataCacheReport().enabled());
        assertTrue(result.renderDataCacheReport().notes().stream()
                .anyMatch(note -> note.contains("unavailable or incompatible manifest")));
        assertFalse(reader.directMapChunkRequests.getLast().isEmpty());
    }

    @Test
    void saveRevisionChangeDoesNotReusePreviousRenderArtifacts() throws Exception {
        Path savePath = temporaryDirectory.resolve("revision-render-data-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("revision-render-data-cache")
        );

        RenderActualOreMapResult first = useCase(
                surfaceReader(true),
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("revision-home.properties"),
                temporaryDirectory.resolve("revision-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertTrue(first.renderDataCacheReport().terrain().published() >= 1);

        FakeReader hitReader = surfaceReader(true);
        RenderActualOreMapResult second = useCase(
                hitReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("revision-home.properties"),
                temporaryDirectory.resolve("revision-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertTrue(second.renderDataCacheReport().terrain().hits() >= 1);
        assertTrue(
                hitReader.directMapChunkRequests.isEmpty(),
                "snapshot-backed Terrain HIT must eliminate the source mapchunk traversal"
        );

        Files.setLastModifiedTime(savePath, FileTime.fromMillis(2_000L));
        FakeReader missReader = surfaceReader(true);
        RenderActualOreMapResult third = useCase(
                missReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("revision-home.properties"),
                temporaryDirectory.resolve("revision-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertEquals(0, third.renderDataCacheReport().terrain().hits());
        assertTrue(third.renderDataCacheReport().terrain().misses() >= 1);
        assertFalse(missReader.directMapChunkRequests.getLast().isEmpty());

        Files.write(savePath, new byte[]{1, 2});
        FakeReader sizeMissReader = surfaceReader(true);
        RenderActualOreMapResult sizeChanged = useCase(
                sizeMissReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("revision-home.properties"),
                temporaryDirectory.resolve("revision-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertEquals(0, sizeChanged.renderDataCacheReport().terrain().hits());
        assertTrue(sizeChanged.renderDataCacheReport().terrain().misses() >= 1);
        assertFalse(sizeMissReader.directMapChunkRequests.getLast().isEmpty());
    }

    @Test
    void terrainCorruptRowFallsBackHealsDeterministicallyAndThenHits() throws Exception {
        Path savePath = temporaryDirectory.resolve("terrain-corrupt-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("terrain-corrupt-cache")
        );
        WorldMetadata metadata = new WorldMetadata(32, 256, 32);
        Path home = temporaryDirectory.resolve("terrain-corrupt-home.properties");
        Path markers = temporaryDirectory.resolve("terrain-corrupt-markers.csv");
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC, Set.of(RenderLayer.TERRAIN),
                Optional.empty(), ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        );

        RenderActualOreMapResult first = useCase(
                surfaceReader(true), metadata, home, markers, cacheStore
        ).execute(request);
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        byte[] expectedPayload = terrainPayload(cacheStore, revision, coordinate);

        corruptTerrainRow(cacheStore, revision, coordinate);
        FakeReader recoveryReader = surfaceReader(true);
        RenderActualOreMapResult recovered = useCase(
                recoveryReader, metadata, home, markers, cacheStore
        ).execute(request);
        assertEquals(1, recovered.renderDataCacheReport().terrain().corruptOrIncompatible());
        assertEquals(1, recovered.renderDataCacheReport().terrain().published());
        assertFalse(recoveryReader.directMapChunkRequests.getLast().isEmpty());
        assertArrayEquals(expectedPayload, terrainPayload(cacheStore, revision, coordinate));

        FakeReader hitReader = surfaceReader(true);
        RenderActualOreMapResult hit = useCase(
                hitReader, metadata, home, markers, cacheStore
        ).execute(request);
        assertEquals(1, hit.renderDataCacheReport().terrain().hits());
        assertTrue(
                hitReader.directMapChunkRequests.isEmpty(),
                "snapshot-backed Terrain HIT must eliminate the source mapchunk traversal"
        );
        assertParity(first, recovered);
        assertParity(first, hit);
    }

    @Test
    void malformedManifestVariantsAndCachePreparationFailureRemainSourceOnly() throws Exception {
        List<String> malformed = List.of(
                "not-a-manifest\n",
                "schemaVersion=render-data-v1\n"
        );
        for (int index = 0; index < malformed.size(); index++) {
            Path savePath = temporaryDirectory.resolve("manifest-variant-" + index + ".vcdbs");
            Files.write(savePath, new byte[]{1});
            RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                    temporaryDirectory.resolve("manifest-variant-cache-" + index)
            );
            RenderDataCacheRevision revision = cacheStore.observe(savePath);
            cacheStore.publish(revision);
            Files.writeString(cacheStore.manifestPath(revision), malformed.get(index));
            FakeReader reader = surfaceReader(true);
            RenderActualOreMapResult source = useCase(
                    surfaceReader(true), new WorldMetadata(32, 256, 32),
                    temporaryDirectory.resolve("manifest-source-home-" + index + ".properties"),
                    temporaryDirectory.resolve("manifest-source-markers-" + index + ".csv")
            ).execute(new RenderActualOreMapRequest(
                    savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            RenderActualOreMapResult result = useCase(
                    reader, new WorldMetadata(32, 256, 32),
                    temporaryDirectory.resolve("manifest-variant-home-" + index + ".properties"),
                    temporaryDirectory.resolve("manifest-variant-markers-" + index + ".csv"),
                    cacheStore
            ).execute(new RenderActualOreMapRequest(
                    savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            assertFalse(result.renderDataCacheReport().enabled());
            assertFalse(reader.directMapChunkRequests.getLast().isEmpty());
            assertParity(source, result);
        }

        String[] incompatibleSchemas = {"render-data-v99", "render-data-v1"};
        String[] incompatibleParsers = {"parser-data-v1", "parser-data-v99"};
        for (int index = 0; index < incompatibleSchemas.length; index++) {
            Path incompatibleSave = temporaryDirectory.resolve("complete-manifest-" + index + ".vcdbs");
            Files.write(incompatibleSave, new byte[]{1});
            RenderDataCacheStore incompatibleCache = new RenderDataCacheStore(
                    temporaryDirectory.resolve("complete-manifest-cache-" + index));
            RenderDataCacheRevision incompatibleRevision = incompatibleCache.observe(incompatibleSave);
            incompatibleCache.publish(incompatibleRevision);
            Files.writeString(incompatibleCache.manifestPath(incompatibleRevision), completeManifest(
                    incompatibleRevision, incompatibleSchemas[index], incompatibleParsers[index]));
            FakeReader incompatibleReader = surfaceReader(true);
            RenderActualOreMapResult incompatibleSource = useCase(
                    surfaceReader(true), new WorldMetadata(32, 256, 32),
                    temporaryDirectory.resolve("complete-manifest-source-home-" + index + ".properties"),
                    temporaryDirectory.resolve("complete-manifest-source-markers-" + index + ".csv")
            ).execute(new RenderActualOreMapRequest(
                    incompatibleSave, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            RenderActualOreMapResult incompatibleResult = useCase(
                    incompatibleReader, new WorldMetadata(32, 256, 32),
                    temporaryDirectory.resolve("complete-manifest-home-" + index + ".properties"),
                    temporaryDirectory.resolve("complete-manifest-markers-" + index + ".csv"),
                    incompatibleCache
            ).execute(new RenderActualOreMapRequest(
                    incompatibleSave, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            assertFalse(incompatibleResult.renderDataCacheReport().enabled());
            assertFalse(incompatibleReader.directMapChunkRequests.getLast().isEmpty());
            assertParity(incompatibleSource, incompatibleResult);
        }

        Path savePath = temporaryDirectory.resolve("cache-preparation-failure.vcdbs");
        Files.write(savePath, new byte[]{1});
        Path cacheFile = temporaryDirectory.resolve("cache-root-is-file");
        Files.write(cacheFile, new byte[]{1});
        FakeReader reader = surfaceReader(true);
        RenderActualOreMapResult source = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("cache-failure-source-home.properties"),
                temporaryDirectory.resolve("cache-failure-source-markers.csv")
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
        ));
        RenderActualOreMapResult result = useCase(
                reader, new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("cache-failure-home.properties"),
                temporaryDirectory.resolve("cache-failure-markers.csv"),
                new RenderDataCacheStore(cacheFile)
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertFalse(result.renderDataCacheReport().enabled());
        assertFalse(reader.directMapChunkRequests.getLast().isEmpty());
        assertParity(source, result);
        assertFalse(Files.exists(savePath.resolveSibling("terrain-cache.sqlite")));
        assertFalse(Files.exists(savePath.resolveSibling("surface-cache.sqlite")));
    }

    @Test
    void terrainHitCanProvideSurfacePlanningAndPopulateSurfaceCache() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("source");
        Path cacheRoot = temporaryDirectory.resolve("cache").resolve("render-data");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(cacheRoot.getParent());
        Path savePath = sourceDirectory.resolve("cached-surface-save.vcdbs");
        Path normalizedSourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        Path normalizedCacheRoot = cacheRoot.toAbsolutePath().normalize();
        assertFalse(normalizedSourceDirectory.equals(normalizedCacheRoot));
        assertFalse(normalizedCacheRoot.startsWith(normalizedSourceDirectory));
        assertFalse(normalizedSourceDirectory.startsWith(normalizedCacheRoot));
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                cacheRoot
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        new TerrainTileStore(cacheStore, revision).publish(List.of(
                new TerrainHeightTile(
                        new MapChunkCoordinate(0, 0), true, true, filledHeights()
                )
        ));

        FakeReader firstReader = surfaceReader(true);
        RenderActualOreMapResult first = useCase(
                firstReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("cached-home.properties"),
                temporaryDirectory.resolve("cached-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertTrue(first.renderDataCacheReport().terrain().hits() >= 1);
        assertEquals(0, first.renderDataCacheReport().terrain().sourceLoaded());
        assertTrue(
                firstReader.directMapChunkRequests.isEmpty(),
                "Terrain cache HIT must not issue an empty source mapchunk traversal"
        );
        assertEquals(1, firstReader.adaptiveExactChunkCalls);
        assertEquals(1, first.renderDataCacheReport().surface().published());
        assertEquals(1, first.renderDataCacheReport().surface().sourceLoaded());
        byte[] expectedSurfacePayload = surfacePayload(cacheStore, revision,
                new MapChunkCoordinate(0, 0));
        assertExplicitCacheArtifactsContained(cacheStore, revision, savePath);

        corruptSurfaceRow(cacheStore, revision);

        FakeReader secondReader = surfaceReader(true);
        RenderActualOreMapResult second = useCase(
                secondReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("cached-home.properties"),
                temporaryDirectory.resolve("cached-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertEquals(1, second.renderDataCacheReport().surface().corruptOrIncompatible());
        assertEquals(1, second.renderDataCacheReport().surface().published());
        assertEquals(1, secondReader.adaptiveExactChunkCalls);
        assertTrue(
                secondReader.directMapChunkRequests.isEmpty(),
                "Terrain cache HIT must remain source-free while Surface heals"
        );
        assertArrayEquals(expectedSurfacePayload, surfacePayload(cacheStore, revision,
                new MapChunkCoordinate(0, 0)));
        assertParity(first, second);

        FakeReader thirdReader = surfaceReader(true);
        RenderActualOreMapResult third = useCase(
                thirdReader,
                new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("cached-home.properties"),
                temporaryDirectory.resolve("cached-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertEquals(1, third.renderDataCacheReport().surface().hits());
        assertEquals(0, third.renderDataCacheReport().surface().sourceLoaded());
        assertEquals(0, thirdReader.adaptiveExactChunkCalls);
        assertParity(first, third);
        assertArrayEquals(expectedSurfacePayload, surfacePayload(cacheStore, revision,
                new MapChunkCoordinate(0, 0)));
        assertExplicitCacheArtifactsContained(cacheStore, revision, savePath);
    }

    @Test
    void clippedSurfaceResultIsNotPublishedAsReusableTile() throws Exception {
        Path savePath = temporaryDirectory.resolve("clipped-surface-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("clipped-render-data-cache")
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        new TerrainTileStore(cacheStore, revision).publish(List.of(
                new TerrainHeightTile(
                        new MapChunkCoordinate(0, 0), true, true, filledHeights()
                )
        ));

        RenderActualOreMapResult result = useCase(
                surfaceReader(true),
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("clipped-home.properties"),
                temporaryDirectory.resolve("clipped-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 16, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertEquals(0, result.renderDataCacheReport().surface().published());
        assertTrue(result.renderDataCacheReport().surface().skippedIncompleteForPublish() >= 1);
    }

    @Test
    void fallbackCachePreservesFullServerChunkDiagnosticsAtWorldEdge() throws Exception {
        Path savePath = temporaryDirectory.resolve("edge-fallback-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("edge-render-data-cache")
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        new TerrainTileStore(cacheStore, revision).publish(List.of(
                new TerrainHeightTile(
                        new MapChunkCoordinate(1, 0), true, true, filledHeights(999)
                )
        ));
        WorldMetadata metadata = new WorldMetadata(34, 256, 32);

        RenderActualOreMapResult first = useCase(
                edgeFallbackReader(), metadata,
                temporaryDirectory.resolve("edge-home.properties"),
                temporaryDirectory.resolve("edge-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 17, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(33, 64, 16))
        ));

        int fullChunkColumns = ChunkCoordinate.SIZE_BLOCKS * ChunkCoordinate.SIZE_BLOCKS;
        assertEquals(fullChunkColumns, first.surface().columnsScanned());
        assertEquals(fullChunkColumns, first.surface().liquidUnavailableColumns());
        assertEquals(1, first.renderDataCacheReport().surface().published());

        FakeReader secondReader = edgeFallbackReader();
        RenderActualOreMapResult second = useCase(
                secondReader, metadata,
                temporaryDirectory.resolve("edge-home.properties"),
                temporaryDirectory.resolve("edge-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 17, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(33, 64, 16))
        ));

        assertEquals(first.surface().columnsScanned(), second.surface().columnsScanned());
        assertEquals(first.surface().emptyColumns(), second.surface().emptyColumns());
        assertEquals(1, second.renderDataCacheReport().surface().hits());
        assertEquals(0, second.renderDataCacheReport().surface().sourceLoaded());
        assertTrue(secondReader.exactRequests.stream()
                .flatMap(List::stream)
                .noneMatch(position -> position.x() == 1 && position.z() == 0));
        assertEquals(first.surface().liquidUnavailableColumns(),
                second.surface().liquidUnavailableColumns());
        assertParity(first, second);
    }

    @Test
    void mixedTerrainHitAndMissReadsOnlyTheMissingMapchunkCoordinates() throws Exception {
        Path savePath = temporaryDirectory.resolve("mixed-terrain-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("mixed-terrain-render-data-cache")
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        MapChunkCoordinate hitCoordinate = new MapChunkCoordinate(0, 0);
        MapChunkCoordinate missCoordinate = new MapChunkCoordinate(1, 1);
        new TerrainTileStore(cacheStore, revision).publish(List.of(
                new TerrainHeightTile(hitCoordinate, true, true, filledHeights())
        ));
        FakeReader reader = new FakeReader(fireClayRegistry());
        reader.mapChunks.put(missCoordinate,
                new MapChunk(missCoordinate, filledHeights(7), new int[0]));

        RenderActualOreMapResult result = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("mixed-terrain-home.properties"),
                temporaryDirectory.resolve("mixed-terrain-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 32, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(32, 64, 32))
        ));

        List<MapChunkCoordinate> requested = reader.directMapChunkRequests.getLast();
        assertFalse(requested.contains(hitCoordinate));
        assertTrue(requested.contains(missCoordinate));
        assertEquals(1, result.renderDataCacheReport().terrain().hits());
        assertTrue(result.renderDataCacheReport().terrain().sourceLoaded() >= 1);

        FakeReader sourceReader = new FakeReader(fireClayRegistry());
        sourceReader.mapChunks.put(hitCoordinate,
                new MapChunk(hitCoordinate, filledHeights(), new int[0]));
        sourceReader.mapChunks.put(missCoordinate,
                new MapChunk(missCoordinate, filledHeights(7), new int[0]));
        RenderActualOreMapResult source = useCase(
                sourceReader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("mixed-terrain-home-source.properties"),
                temporaryDirectory.resolve("mixed-terrain-markers-source.csv")
        ).execute(new RenderActualOreMapRequest(
                savePath, 32, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(32, 64, 32))
        ));
        assertParity(source, result);
    }

    @Test
    void productionMixedSurfaceHitAndFallbackMissKeepsHitTileOutOfSourceWork() throws Exception {
        Path savePath = temporaryDirectory.resolve("mixed-surface-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("mixed-surface-render-data-cache")
        );
        WorldMetadata metadata = new WorldMetadata(64, 256, 32);

        FakeReader firstReader = surfaceReader(true);
        RenderActualOreMapResult first = useCase(
                firstReader, metadata,
                temporaryDirectory.resolve("mixed-surface-home.properties"),
                temporaryDirectory.resolve("mixed-surface-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));
        assertEquals(1, first.renderDataCacheReport().surface().published());

        FakeReader secondReader = new FakeReader(fireClayRegistry());
        MapChunkCoordinate fallbackCoordinate = new MapChunkCoordinate(1, 0);
        secondReader.mapChunks.put(
                fallbackCoordinate,
                new MapChunk(fallbackCoordinate, filledHeights(999), new int[0])
        );
        secondReader.chunks.put(
                new ChunkPosition(1, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(1, 0, 0), true)
        );
        RenderActualOreMapResult second = useCase(
                secondReader, metadata,
                temporaryDirectory.resolve("mixed-surface-home.properties"),
                temporaryDirectory.resolve("mixed-surface-markers.csv"),
                cacheStore
        ).execute(new RenderActualOreMapRequest(
                savePath, 15, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.SURFACE), Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(31, 64, 16))
        ));

        assertEquals(1, second.renderDataCacheReport().surface().hits());
        assertEquals(1, second.renderDataCacheReport().surface().misses());
        assertEquals(1, second.renderDataCacheReport().surface().sourceLoaded());
        assertTrue(hasSurfaceCode(second, "game:fire-clay-blue"));
        assertTrue(hasSurfaceXAtLeast(second, 32));
        assertTrue(secondReader.directMapChunkRequests.getLast().contains(fallbackCoordinate));
        assertTrue(secondReader.directMapChunkRequests.getLast().stream()
                .allMatch(coordinate -> coordinate.equals(fallbackCoordinate)));
        assertTrue(secondReader.exactRequests.stream()
                .flatMap(List::stream)
                .allMatch(position -> position.x() == fallbackCoordinate.x()));

        assertTrue(hasSurfaceXLessThan(second, 32));
        assertTrue(hasSurfaceXAtLeast(second, 32));
    }

    @Test
    void sourceMissAndHitPreserveSemanticAndImageFingerprints() throws Exception {
        Path savePath = temporaryDirectory.resolve("fingerprint-parity-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        Path home = temporaryDirectory.resolve("fingerprint-parity-home.properties");
        Path markers = temporaryDirectory.resolve("fingerprint-parity-markers.csv");
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC, Set.of(RenderLayer.SURFACE),
                Optional.empty(), ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        );

        RenderActualOreMapResult source = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32), home, markers
        ).execute(request);
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                temporaryDirectory.resolve("fingerprint-parity-cache")
        );
        RenderActualOreMapResult miss = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32), home, markers, cacheStore
        ).execute(request);
        RenderActualOreMapResult hit = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32), home, markers, cacheStore
        ).execute(request);

        assertParity(source, miss);
        assertParity(source, hit);
        assertTrue(miss.renderDataCacheReport().terrain().misses() > 0);
        assertTrue(hit.renderDataCacheReport().terrain().hits() > 0);
        assertTrue(hit.renderDataCacheReport().surface().hits() > 0);
    }

    @Test
    void productionRenderUsesOneSourceConnectionAcrossMultipleReaderActions() {
        TestConnectionFactory connections = new TestConnectionFactory();
        FakeReader reader = surfaceReader(true);
        RenderActualOreMapResult result = useCase(
                reader, new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("lifecycle-home.properties"),
                temporaryDirectory.resolve("lifecycle-markers.csv"), connections
        ).execute(new RenderActualOreMapRequest(
                temporaryDirectory.resolve("lifecycle-save.vcdbs"), 23, 1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.ENVIRONMENT),
                Optional.empty(), ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        ));

        assertTrue(result.surface().chunksScanned() >= 0);
        assertTrue(reader.sessionMapRegionCalls > 0);
        assertEquals(1, connections.opened());
        assertEquals(1, connections.closed());
    }

    @Test
    void separateProductionOperationsDoNotShareLifecycleState() {
        TestConnectionFactory firstConnections = new TestConnectionFactory();
        TestConnectionFactory secondConnections = new TestConnectionFactory();
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("isolated-save.vcdbs"), 23, 1,
                RenderStyle.TOPOGRAPHIC, Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
        );

        useCase(surfaceReader(true), new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("isolated-one-home.properties"),
                temporaryDirectory.resolve("isolated-one-markers.csv"), firstConnections)
                .execute(request);
        useCase(surfaceReader(true), new WorldMetadata(32, 256, 32),
                temporaryDirectory.resolve("isolated-two-home.properties"),
                temporaryDirectory.resolve("isolated-two-markers.csv"), secondConnections)
                .execute(request);

        assertEquals(1, firstConnections.opened());
        assertEquals(1, firstConnections.closed());
        assertEquals(1, secondConnections.opened());
        assertEquals(1, secondConnections.closed());
    }

    private static void assertParity(
            RenderActualOreMapResult expected,
            RenderActualOreMapResult actual
    ) {
        assertEquals(expected.geometry(), actual.geometry());
        assertEquals(expected.surface().columnsScanned(), actual.surface().columnsScanned());
        assertEquals(expected.surface().emptyColumns(), actual.surface().emptyColumns());
        assertEquals(
                expected.surface().liquidUnavailableColumns(),
                actual.surface().liquidUnavailableColumns()
        );
        assertEquals(expected.surface().waterColumns(), actual.surface().waterColumns());
        assertEquals(
                expected.surface().unknownSurfaceBlocks(),
                actual.surface().unknownSurfaceBlocks()
        );
        assertEquals(
                expected.surface().topUnknownSurfaceBlockCodes(Integer.MAX_VALUE),
                actual.surface().topUnknownSurfaceBlockCodes(Integer.MAX_VALUE)
        );
        assertEquals(
                expected.surface().distinctSurfaceBlockCodes(Integer.MAX_VALUE),
                actual.surface().distinctSurfaceBlockCodes(Integer.MAX_VALUE)
        );
        assertImageEquals(expected.image(), actual.image());
    }

    @Test
    void environmentOverlayUsesSessionMapRegionReader() {
        FakeReader reader = new FakeReader(Map.of());

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.ENVIRONMENT),
                64,
                64,
                16
        );

        assertEquals(1, reader.sessionMapRegionCalls);
        assertTrue(result.mapRegionOverlayState().isPresent());
        assertTrue(result.mapRegionOverlayState().orElseThrow().environmentPrepared());
        assertFalse(result.mapRegionOverlayState().orElseThrow().geologyPrepared());
    }

    @Test
    void surfaceLayerUsesRainHeightFastPath() {
        FakeReader reader = surfaceReader(true);

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.exactRequests.getFirst().size());
        assertTrue(hasSurfaceCode(result, "game:fire-clay-blue"));
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
    }

    @Test
    void soilFertilityOnlyUsesTheExistingSurfacePipeline() {
        FakeReader reader = fertilityReader();

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.SOIL_FERTILITY),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.registryCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertTrue(hasSurfaceCode(result, "game:soil-medium-normal"));
        assertTrue(result.image().getRGB(32, 32)
                != new cartographer.render.TerrainPalette()
                .background(RenderStyle.TOPOGRAPHIC));
    }

    @Test
    void surfaceAndSoilFertilityShareOneSurfacePipeline() {
        FakeReader reader = fertilityReader();

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.SURFACE, RenderLayer.SOIL_FERTILITY),
                16,
                16,
                1
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertEquals(1, reader.adaptiveExactChunkCalls);
        assertEquals(1, reader.exactChunkCalls);
        assertEquals(1, reader.registryCalls);
        assertEquals(0, reader.legacyMapChunkCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertTrue(hasSurfaceCode(result, "game:soil-medium-normal"));
    }

    @Test
    void surfaceFallbackIsLocalToProblematicMapChunk() {
        FakeReader reader = surfaceReader(true);
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 0);
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 2; x++) {
                MapChunkCoordinate coordinate = new MapChunkCoordinate(x, z);
                if (coordinate.equals(fallback)) {
                    continue;
                }
                reader.mapChunks.put(
                        coordinate,
                        new MapChunk(coordinate, filledHeights(), new int[0])
                );
                reader.chunks.put(
                        new ChunkPosition(x, 0, z, 0),
                        surfaceChunk(new ChunkCoordinate(x, 0, z), true)
                );
            }
        }
        reader.mapChunks.put(fallback, new MapChunk(fallback, new int[0], filledHeights()));
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(fallback.x(), y, fallback.z(), 0),
                    surfaceChunk(new ChunkCoordinate(fallback.x(), y, fallback.z()), true)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                32,
                0,
                32
        );

        assertEquals(2, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream()
                .noneMatch(position -> position.x() == fallback.x()
                        && position.z() == fallback.z()));
        assertEquals(8, reader.exactRequests.get(1).size());
        assertTrue(reader.exactRequests.get(1).stream()
                .allMatch(position -> position.x() == fallback.x()
                        && position.z() == fallback.z()));
        assertTrue(hasSurfaceXLessThan(result, 32));
        assertTrue(hasSurfaceXAtLeast(result, 32));
    }

    @Test
    void fractionalCenterSurfaceSearchUsesUnionMapChunkLookup() {
        FakeReader reader = new FakeReader(Map.of());
        MapChunkCoordinate surfaceOnly = new MapChunkCoordinate(2, 1);
        reader.mapChunks.put(surfaceOnly, new MapChunk(surfaceOnly, filledHeights(), new int[0]));
        reader.chunks.put(
                new ChunkPosition(2, 0, 1, 0),
                surfaceChunk(new ChunkCoordinate(2, 0, 1), true)
        );
        for (int z = 0; z <= 2; z++) {
            for (int x = 0; x <= 1; x++) {
                MapChunkCoordinate coordinate = new MapChunkCoordinate(x, z);
                reader.mapChunks.put(coordinate, new MapChunk(coordinate, filledHeights(), new int[0]));
                reader.chunks.put(
                        new ChunkPosition(x, 0, z, 0),
                        surfaceChunk(new ChunkCoordinate(x, 0, z), true)
                );
            }
        }

        execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                31.6,
                48.0,
                32,
                new WorldMetadata(128, 256, 128)
        );

        assertEquals(1, reader.directMapChunkCalls);
        assertTrue(reader.directMapChunkRequests.getFirst().contains(surfaceOnly));
        assertEquals(1, reader.exactChunkCalls);
        assertTrue(reader.exactRequests.getFirst().stream()
                .anyMatch(position -> position.x() == surfaceOnly.x()
                        && position.z() == surfaceOnly.z()));
    }

    @Test
    void unresolvedFastTargetFallsBackWholeMapChunk() {
        FakeReader reader = surfaceReader(false);
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(0, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(0, y, 0), false)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                16,
                16,
                1
        );

        assertEquals(2, reader.exactChunkCalls);
        assertEquals(8, reader.exactRequests.get(1).size());
        assertTrue(result.actualOreMap().isEmpty());
        assertTrue(hasSurfaceCode(result, "game:fire-clay-blue"));
    }

    @Test
    void resultSurfaceContainsMergedFastAndFallbackBlocks() {
        FakeReader reader = surfaceReader(true);
        MapChunkCoordinate fallback = new MapChunkCoordinate(1, 0);
        reader.mapChunks.put(fallback, new MapChunk(fallback, new int[0], filledHeights()));
        for (int y = 0; y < 8; y++) {
            reader.chunks.put(
                    new ChunkPosition(1, y, 0, 0),
                    surfaceChunk(new ChunkCoordinate(1, y, 0), true)
            );
        }

        RenderActualOreMapResult result = execute(
                reader,
                List.of(),
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                32,
                0,
                32
        );

        assertTrue(hasSurfaceXLessThan(result, 32));
        assertTrue(hasSurfaceXAtLeast(result, 32));
    }

    private RenderActualOreMapResult execute(FakeReader reader) {
        return execute(
                reader,
                List.of(new ActualOreOverlaySpec(
                        "Cassiterite",
                        "cassiterite",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );
    }

    private boolean hasSurfaceCode(
            RenderActualOreMapResult result,
            String code
    ) {
        return result.surface()
                .distinctSurfaceBlockCodes(Integer.MAX_VALUE)
                .contains(code);
    }

    private boolean hasSurfaceXLessThan(
            RenderActualOreMapResult result,
            int bound
    ) {
        var renderData = result.preparedMapData()
                .orElseThrow()
                .surface()
                .renderData();
        for (int y = 0; y < renderData.rasterSize(); y++) {
            for (int x = 0; x < renderData.rasterSize(); x++) {
                if (cartographer.render.SurfaceRenderDataTestAccess.hasSurfaceAt(renderData, x, y)
                        && cartographer.render.SurfaceRenderDataTestAccess.surfaceWorldXAt(renderData, x, y) < bound) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSurfaceXAtLeast(
            RenderActualOreMapResult result,
            int bound
    ) {
        var renderData = result.preparedMapData()
                .orElseThrow()
                .surface()
                .renderData();
        for (int y = 0; y < renderData.rasterSize(); y++) {
            for (int x = 0; x < renderData.rasterSize(); x++) {
                if (cartographer.render.SurfaceRenderDataTestAccess.hasSurfaceAt(renderData, x, y)
                        && cartographer.render.SurfaceRenderDataTestAccess.surfaceWorldXAt(renderData, x, y) >= bound) {
                    return true;
                }
            }
        }
        return false;
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs
    ) {
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                temporaryDirectory.resolve("home.properties"),
                temporaryDirectory.resolve("markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                specs
        );
        return useCase.execute(request);
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs,
            Set<RenderLayer> layers,
            double centerX,
            double centerZ,
            int radius
    ) {
        return execute(reader, specs, layers, centerX, centerZ, radius,
                new WorldMetadata(128, 256, 128));
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs,
            Set<RenderLayer> layers,
            double centerX,
            double centerZ,
            int radius,
            WorldMetadata metadata
    ) {
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                metadata,
                temporaryDirectory.resolve("home-surface.properties"),
                temporaryDirectory.resolve("markers-surface.csv")
        );
        return useCase.execute(new RenderActualOreMapRequest(
                temporaryDirectory.resolve("surface-save.vcdbs"), radius, 1,
                RenderStyle.TOPOGRAPHIC, layers, Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(centerX, 64, centerZ)), specs
        ));
    }

    private RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader,
                new HomeStore(homePath),
                new MarkerStore(markerPath),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader)
        );
    }

    private RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath,
            RenderDataCacheStore renderDataCacheStore
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader,
                new HomeStore(homePath),
                new MarkerStore(markerPath),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader),
                renderDataCacheStore
        );
    }

    private RenderActualOreMapUseCase useCase(
            FakeReader reader,
            WorldMetadata metadata,
            Path homePath,
            Path markerPath,
            TestConnectionFactory connections
    ) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                return metadata;
            }
        };
        return new RenderActualOreMapUseCase(
                reader, new HomeStore(homePath), new MarkerStore(markerPath),
                new MapRenderer(), new UserMarkerRenderer(),
                new ActualOreOverlayPainter(), new cartographer.scanner.MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(),
                new SaveSessionFactory(connections, reader, metadataReader)
        );
    }

    private static void assertSurfaceParity(
            cartographer.scanner.SurfaceMap expected,
            cartographer.scanner.SurfaceMap actual
    ) {
        assertEquals(expected.layout().tileCount(), actual.layout().tileCount());
        for (int tileIndex = 0; tileIndex < expected.layout().tileCount(); tileIndex++) {
            cartographer.scanner.SurfaceTile expectedTile = expected.tileAt(tileIndex);
            cartographer.scanner.SurfaceTile actualTile = actual.tileAt(tileIndex);
            assertEquals(expectedTile.width(), actualTile.width());
            assertEquals(expectedTile.height(), actualTile.height());
            for (int localZ = 0; localZ < expectedTile.height(); localZ++) {
                for (int localX = 0; localX < expectedTile.width(); localX++) {
                    assertEquals(expectedTile.isActive(localX, localZ), actualTile.isActive(localX, localZ));
                    assertEquals(expectedTile.isConsidered(localX, localZ), actualTile.isConsidered(localX, localZ));
                    assertEquals(expectedTile.isResolved(localX, localZ), actualTile.isResolved(localX, localZ));
                    assertEquals(expectedTile.isLiquidUnavailable(localX, localZ), actualTile.isLiquidUnavailable(localX, localZ));
                    assertEquals(expectedTile.surfaceYAt(localX, localZ), actualTile.surfaceYAt(localX, localZ));
                    assertEquals(expectedTile.blockIdAt(localX, localZ), actualTile.blockIdAt(localX, localZ));
                    assertEquals(expectedTile.liquidBlockIdAt(localX, localZ), actualTile.liquidBlockIdAt(localX, localZ));
                    assertEquals(expectedTile.surfaceClassAt(localX, localZ), actualTile.surfaceClassAt(localX, localZ));
                }
            }
        }
    }

    private static void corruptSurfaceRow(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision
    ) throws Exception {
        Path database = new SurfaceTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE surface_tile SET payload = X'00'");
        }
    }

    private static void corruptTerrainRow(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new TerrainTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE terrain_tile SET payload = X'00' "
                    + "WHERE mapchunk_x = " + coordinate.x()
                    + " AND mapchunk_z = " + coordinate.z());
        }
    }

    private static byte[] terrainPayload(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new TerrainTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT payload FROM terrain_tile WHERE mapchunk_x = "
                             + coordinate.x() + " AND mapchunk_z = " + coordinate.z())) {
            assertTrue(resultSet.next());
            return resultSet.getBytes("payload");
        }
    }

    private static byte[] surfacePayload(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            MapChunkCoordinate coordinate
    ) throws Exception {
        Path database = new SurfaceTileStore(cacheStore, revision).databasePath();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT payload FROM surface_tile WHERE mapchunk_x = "
                             + coordinate.x() + " AND mapchunk_z = " + coordinate.z())) {
            assertTrue(resultSet.next());
            return resultSet.getBytes("payload");
        }
    }

    private static String completeManifest(
            RenderDataCacheRevision revision,
            String schemaVersion,
            String compatibilityVersion
    ) {
        String encodedPath = Base64.getEncoder().encodeToString(
                revision.identity().normalizedSavePath().toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        return "schemaVersion=" + schemaVersion + "\n"
                + "normalizedSavePathBase64=" + encodedPath + "\n"
                + "namespaceHash=" + revision.identity().namespaceHash() + "\n"
                + "saveSize=" + revision.saveSize() + "\n"
                + "saveModifiedMillis=" + revision.saveModifiedMillis() + "\n"
                + "compatibilityVersion=" + compatibilityVersion + "\n";
    }

    private static void assertExplicitCacheArtifactsContained(
            RenderDataCacheStore cacheStore,
            RenderDataCacheRevision revision,
            Path savePath
    ) throws Exception {
        Path root = cacheStore.cacheRoot().toAbsolutePath().normalize();
        Path sourceDirectory = savePath.toAbsolutePath().normalize().getParent();
        List<Path> artifacts = List.of(
                cacheStore.manifestPath(revision),
                new TerrainTileStore(cacheStore, revision).databasePath(),
                new SurfaceTileStore(cacheStore, revision).databasePath()
        );
        for (Path artifact : artifacts) {
            Path normalized = artifact.toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(normalized),
                    "expected PF-1.7 artifact is missing: " + normalized);
            assertTrue(normalized.startsWith(root),
                    "PF-1.7 artifact escaped cache root: " + normalized);
            assertFalse(normalized.startsWith(sourceDirectory),
                    "PF-1.7 artifact was placed beside the source save: " + normalized);
        }
        for (String name : List.of("manifest.properties", "terrain-cache.sqlite",
                "surface-cache.sqlite")) {
            assertFalse(Files.exists(savePath.resolveSibling(name)),
                    "cache artifact appeared beside source save: " + name);
        }
        Path revisionDirectory = cacheStore.manifestPath(revision).getParent();
        try (var paths = Files.list(revisionDirectory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".manifest-")),
                    "temporary manifest publication file survived");
        }
    }

    private FakeReader surfaceReader(boolean liquidAvailable) {
        FakeReader reader = new FakeReader(fireClayRegistry());
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        reader.mapChunks.put(
                coordinate,
                new MapChunk(coordinate, filledHeights(), new int[0])
        );
        reader.chunks.put(
                new ChunkPosition(0, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(0, 0, 0), liquidAvailable)
        );
        return reader;
    }

    private FakeReader fertilityReader() {
        FakeReader reader = new FakeReader(Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:soil-medium-normal")
        ));
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        reader.mapChunks.put(
                coordinate,
                new MapChunk(coordinate, filledHeights(), new int[0])
        );
        reader.chunks.put(
                new ChunkPosition(0, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(0, 0, 0), true)
        );
        return reader;
    }

    private Map<Integer, BlockInfo> fireClayRegistry() {
        return Map.of(
                0, new BlockInfo(0, "air"),
                1, new BlockInfo(1, "game:fire-clay-blue")
        );
    }

    private ParsedChunk surfaceChunk(
            ChunkCoordinate coordinate,
            boolean liquidAvailable
    ) {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        int[] liquids = liquidAvailable ? new int[blocks.length] : null;
        String liquidDecodeError = liquidAvailable
                ? ""
                : "liquid layer unavailable for test";
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                blocks[(5 * size + z) * size + x] = 1;
            }
        }
        return cartographer.model.ParsedChunkFixtures.create(coordinate, 0, size, size, size, blocks, liquids,
                0, liquidAvailable, liquidDecodeError);
    }

    private static int[] filledHeights() {
        return filledHeights(5);
    }

    private static int[] filledHeights(int value) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        java.util.Arrays.fill(heights, value);
        return heights;
    }

    private FakeReader edgeFallbackReader() {
        FakeReader reader = new FakeReader(fireClayRegistry());
        reader.chunks.put(
                new ChunkPosition(1, 0, 0, 0),
                surfaceChunk(new ChunkCoordinate(1, 0, 0), false)
        );
        return reader;
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private final Map<ChunkPosition, ParsedChunk> chunks = new HashMap<>();
        private final Map<MapChunkCoordinate, MapChunk> mapChunks = new HashMap<>();
        private final List<List<MapChunkCoordinate>> directMapChunkRequests = new ArrayList<>();
        private final List<List<ChunkPosition>> exactRequests = new ArrayList<>();
        private int selectiveCalls;
        private int adaptiveSelectiveCalls;
        private int directMapChunkCalls;
        private int exactChunkCalls;
        private int adaptiveExactChunkCalls;
        private int registryCalls;
        private int sessionMapRegionCalls;
        private int legacyMapChunkCalls;
        private int legacyChunkCalls;
        private final int fakeBlockId;
        private int[] lastWantedBlockIds = new int[0];
        private List<cartographer.model.ChunkPosition> lastPositions = List.of();

        private FakeReader(Map<Integer, BlockInfo> registry) {
            this(registry, 1);
        }

        private FakeReader(Map<Integer, BlockInfo> registry, int fakeBlockId) {
            super(null, null, null, null);
            this.registry = registry;
            this.fakeBlockId = fakeBlockId;
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(64, 64, 64);
        }

        private MapChunkStreamStats visitMapChunks(
                java.util.Collection<MapChunkCoordinate> coordinates,
                java.util.function.Consumer<MapChunk> consumer
        ) {
            directMapChunkCalls++;
            int delivered = 0;
            for (MapChunkCoordinate coordinate : coordinates) {
                MapChunk mapChunk = mapChunks.get(coordinate);
                if (mapChunk != null) {
                    delivered++;
                    consumer.accept(mapChunk);
                }
            }
            return new MapChunkStreamStats(
                    coordinates.size(),
                    coordinates.isEmpty() ? 0 : 1,
                    delivered,
                    delivered,
                    0,
                    0
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                java.util.Collection<MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                ProgressReporter progress
        ) {
            directMapChunkRequests.add(List.copyOf(coordinates));
            return visitMapChunks(coordinates, consumer);
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveExactChunkCalls++;
            return visitChunks(positions, consumer);
        }

        @Override
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveExactChunkCalls++;
            return visitChunks(
                    positions,
                    consumer
            );
        }

        private ChunkStreamStats visitChunks(
                java.util.Collection<ChunkPosition> positions,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            exactChunkCalls++;
            exactRequests.add(List.copyOf(positions));
            int delivered = 0;
            for (ChunkPosition position : positions) {
                ParsedChunk chunk = chunks.get(position);
                if (chunk != null) {
                    delivered++;
                    consumer.accept(chunk);
                }
            }
            return new ChunkStreamStats(
                    positions.size(),
                    positions.isEmpty() ? 0 : 1,
                    delivered,
                    delivered,
                    0,
                    0
            );
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                SaveSession session,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            legacyMapChunkCalls++;
            throw new AssertionError("legacy mapchunk lookup must not be used");
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                SaveSession session,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            legacyChunkCalls++;
            throw new AssertionError("legacy chunk lookup must not be used");
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            registryCalls++;
            return registry;
        }

        @Override
        public List<cartographer.model.ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            sessionMapRegionCalls++;
            return List.of();
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                ProgressReporter progress
        ) {
            adaptiveSelectiveCalls++;
            return visitSelective(positions, wantedBlockIds, consumer);
        }

        private SelectiveChunkStreamStats visitSelective(
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            selectiveCalls++;
            lastWantedBlockIds = wantedBlockIds.clone();
            lastPositions = List.copyOf(positions);
            if (contains(wantedBlockIds, fakeBlockId)) {
                int[] blocks = new int[]{fakeBlockId};
                consumer.accept(cartographer.model.ParsedChunkFixtures.create(
                        new ChunkCoordinate(2, 0, 2),
                        5,
                        1,
                        1,
                        1,
                        blocks
                ));
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    1,
                    registry.containsKey(1) ? 1 : 0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    1
            );
        }

        private boolean contains(int[] values, int wanted) {
            for (int value : values) {
                if (value == wanted) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        private int opened;
        private int closed;

        @Override
        public Connection openReadOnly(Path savePath) {
            opened++;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")
                                && method.getParameterCount() == 0) {
                            closed++;
                        }
                        return null;
                    }
            );
        }

        private int opened() {
            return opened;
        }

        private int closed() {
            return closed;
        }
    }
}
