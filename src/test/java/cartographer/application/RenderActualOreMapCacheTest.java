package cartographer.application;

import cartographer.testing.IntegrationTest;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheRevision;
import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileStore;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class RenderActualOreMapCacheTest extends RenderActualOreMapUseCaseTestSupport {

    @TempDir
    Path suiteTemporaryDirectory;

    @Test
    void malformedFinalManifestDisablesCacheAndUsesSource() throws Exception {
        Path savePath = suiteTemporaryDirectory.resolve("malformed-render-data-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("malformed-render-data-cache")
        );
        RenderDataCacheRevision revision = cacheStore.observe(savePath);
        cacheStore.publish(revision);
        Files.writeString(cacheStore.manifestPath(revision), "not-a-manifest\n");

        FakeReader reader = surfaceReader(true);
        RenderActualOreMapResult result = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                suiteTemporaryDirectory.resolve("malformed-home.properties"),
                suiteTemporaryDirectory.resolve("malformed-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve("revision-render-data-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("revision-render-data-cache")
        );

        RenderActualOreMapResult first = useCase(
                surfaceReader(true),
                new WorldMetadata(32, 256, 32),
                suiteTemporaryDirectory.resolve("revision-home.properties"),
                suiteTemporaryDirectory.resolve("revision-markers.csv"),
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
                suiteTemporaryDirectory.resolve("revision-home.properties"),
                suiteTemporaryDirectory.resolve("revision-markers.csv"),
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
                suiteTemporaryDirectory.resolve("revision-home.properties"),
                suiteTemporaryDirectory.resolve("revision-markers.csv"),
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
                suiteTemporaryDirectory.resolve("revision-home.properties"),
                suiteTemporaryDirectory.resolve("revision-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve("terrain-corrupt-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("terrain-corrupt-cache")
        );
        WorldMetadata metadata = new WorldMetadata(32, 256, 32);
        Path home = suiteTemporaryDirectory.resolve("terrain-corrupt-home.properties");
        Path markers = suiteTemporaryDirectory.resolve("terrain-corrupt-markers.csv");
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
            Path savePath = suiteTemporaryDirectory.resolve("manifest-variant-" + index + ".vcdbs");
            Files.write(savePath, new byte[]{1});
            RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                    suiteTemporaryDirectory.resolve("manifest-variant-cache-" + index)
            );
            RenderDataCacheRevision revision = cacheStore.observe(savePath);
            cacheStore.publish(revision);
            Files.writeString(cacheStore.manifestPath(revision), malformed.get(index));
            FakeReader reader = surfaceReader(true);
            RenderActualOreMapResult source = useCase(
                    surfaceReader(true), new WorldMetadata(32, 256, 32),
                    suiteTemporaryDirectory.resolve("manifest-source-home-" + index + ".properties"),
                    suiteTemporaryDirectory.resolve("manifest-source-markers-" + index + ".csv")
            ).execute(new RenderActualOreMapRequest(
                    savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            RenderActualOreMapResult result = useCase(
                    reader, new WorldMetadata(32, 256, 32),
                    suiteTemporaryDirectory.resolve("manifest-variant-home-" + index + ".properties"),
                    suiteTemporaryDirectory.resolve("manifest-variant-markers-" + index + ".csv"),
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
            Path incompatibleSave = suiteTemporaryDirectory.resolve("complete-manifest-" + index + ".vcdbs");
            Files.write(incompatibleSave, new byte[]{1});
            RenderDataCacheStore incompatibleCache = new RenderDataCacheStore(
                    suiteTemporaryDirectory.resolve("complete-manifest-cache-" + index));
            RenderDataCacheRevision incompatibleRevision = incompatibleCache.observe(incompatibleSave);
            incompatibleCache.publish(incompatibleRevision);
            Files.writeString(incompatibleCache.manifestPath(incompatibleRevision), completeManifest(
                    incompatibleRevision, incompatibleSchemas[index], incompatibleParsers[index]));
            FakeReader incompatibleReader = surfaceReader(true);
            RenderActualOreMapResult incompatibleSource = useCase(
                    surfaceReader(true), new WorldMetadata(32, 256, 32),
                    suiteTemporaryDirectory.resolve("complete-manifest-source-home-" + index + ".properties"),
                    suiteTemporaryDirectory.resolve("complete-manifest-source-markers-" + index + ".csv")
            ).execute(new RenderActualOreMapRequest(
                    incompatibleSave, 23, 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
            ));
            RenderActualOreMapResult incompatibleResult = useCase(
                    incompatibleReader, new WorldMetadata(32, 256, 32),
                    suiteTemporaryDirectory.resolve("complete-manifest-home-" + index + ".properties"),
                    suiteTemporaryDirectory.resolve("complete-manifest-markers-" + index + ".csv"),
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

        Path savePath = suiteTemporaryDirectory.resolve("cache-preparation-failure.vcdbs");
        Files.write(savePath, new byte[]{1});
        Path cacheFile = suiteTemporaryDirectory.resolve("cache-root-is-file");
        Files.write(cacheFile, new byte[]{1});
        FakeReader reader = surfaceReader(true);
        RenderActualOreMapResult source = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32),
                suiteTemporaryDirectory.resolve("cache-failure-source-home.properties"),
                suiteTemporaryDirectory.resolve("cache-failure-source-markers.csv")
        ).execute(new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN), Optional.empty(),
                ActualBlockYFilter.unbounded(), Optional.of(new WorldPosition(16, 64, 16))
        ));
        RenderActualOreMapResult result = useCase(
                reader, new WorldMetadata(32, 256, 32),
                suiteTemporaryDirectory.resolve("cache-failure-home.properties"),
                suiteTemporaryDirectory.resolve("cache-failure-markers.csv"),
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
        Path sourceDirectory = suiteTemporaryDirectory.resolve("source");
        Path cacheRoot = suiteTemporaryDirectory.resolve("cache").resolve("render-data");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(cacheRoot.getParent());
        Path savePath = sourceDirectory.resolve("cached-surface-save.vcdbs");
        Path normalizedSourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        Path normalizedCacheRoot = cacheRoot.toAbsolutePath().normalize();
        assertNotEquals(normalizedSourceDirectory, normalizedCacheRoot);
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
                suiteTemporaryDirectory.resolve("cached-home.properties"),
                suiteTemporaryDirectory.resolve("cached-markers.csv"),
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
                suiteTemporaryDirectory.resolve("cached-home.properties"),
                suiteTemporaryDirectory.resolve("cached-markers.csv"),
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
                suiteTemporaryDirectory.resolve("cached-home.properties"),
                suiteTemporaryDirectory.resolve("cached-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve("clipped-surface-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("clipped-render-data-cache")
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
                suiteTemporaryDirectory.resolve("clipped-home.properties"),
                suiteTemporaryDirectory.resolve("clipped-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve("edge-fallback-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("edge-render-data-cache")
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
                suiteTemporaryDirectory.resolve("edge-home.properties"),
                suiteTemporaryDirectory.resolve("edge-markers.csv"),
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
                suiteTemporaryDirectory.resolve("edge-home.properties"),
                suiteTemporaryDirectory.resolve("edge-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve("mixed-terrain-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("mixed-terrain-render-data-cache")
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
                suiteTemporaryDirectory.resolve("mixed-terrain-home.properties"),
                suiteTemporaryDirectory.resolve("mixed-terrain-markers.csv"),
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
                suiteTemporaryDirectory.resolve("mixed-terrain-home-source.properties"),
                suiteTemporaryDirectory.resolve("mixed-terrain-markers-source.csv")
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
        Path savePath = suiteTemporaryDirectory.resolve("mixed-surface-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("mixed-surface-render-data-cache")
        );
        WorldMetadata metadata = new WorldMetadata(64, 256, 32);

        FakeReader firstReader = surfaceReader(true);
        RenderActualOreMapResult first = useCase(
                firstReader, metadata,
                suiteTemporaryDirectory.resolve("mixed-surface-home.properties"),
                suiteTemporaryDirectory.resolve("mixed-surface-markers.csv"),
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
                suiteTemporaryDirectory.resolve("mixed-surface-home.properties"),
                suiteTemporaryDirectory.resolve("mixed-surface-markers.csv"),
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
        assertTrue(hasSurfaceXAtLeast(second));
        assertTrue(secondReader.directMapChunkRequests.getLast().contains(fallbackCoordinate));
        assertTrue(secondReader.directMapChunkRequests.getLast().stream()
                .allMatch(coordinate -> coordinate.equals(fallbackCoordinate)));
        assertTrue(secondReader.exactRequests.stream()
                .flatMap(List::stream)
                .allMatch(position -> position.x() == fallbackCoordinate.x()));

        assertTrue(hasSurfaceXLessThan(second));
        assertTrue(hasSurfaceXAtLeast(second));
    }

    @Test
    void sourceMissAndHitPreserveSemanticAndImageFingerprints() throws Exception {
        Path savePath = suiteTemporaryDirectory.resolve("fingerprint-parity-save.vcdbs");
        Files.write(savePath, new byte[]{1});
        Path home = suiteTemporaryDirectory.resolve("fingerprint-parity-home.properties");
        Path markers = suiteTemporaryDirectory.resolve("fingerprint-parity-markers.csv");
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                savePath, 23, 1, RenderStyle.TOPOGRAPHIC, Set.of(RenderLayer.SURFACE),
                Optional.empty(), ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(16, 64, 16))
        );

        RenderActualOreMapResult source = useCase(
                surfaceReader(true), new WorldMetadata(32, 256, 32), home, markers
        ).execute(request);
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("fingerprint-parity-cache")
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
}
