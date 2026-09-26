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
class RenderActualOreMapSnapshotTest extends RenderActualOreMapUseCaseTestSupport {

    @TempDir
    Path suiteTemporaryDirectory;

    @Test
    void retainedOreRenderSkipsBasePreparationButStillScansOreAuthoritatively() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapUseCase useCase = useCase(
                reader,
                new WorldMetadata(128, 256, 128),
                suiteTemporaryDirectory.resolve("retained-ore-home.properties"),
                suiteTemporaryDirectory.resolve("retained-ore-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                suiteTemporaryDirectory.resolve("retained-ore-save.vcdbs"),
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
                suiteTemporaryDirectory.resolve("cross-save-home.properties"),
                suiteTemporaryDirectory.resolve("cross-save-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                suiteTemporaryDirectory.resolve("cross-save-a.vcdbs"),
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
                        suiteTemporaryDirectory.resolve("cross-save-b.vcdbs"),
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
                suiteTemporaryDirectory.resolve("retained-region-home.properties"),
                suiteTemporaryDirectory.resolve("retained-region-markers.csv")
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                suiteTemporaryDirectory.resolve("retained-region-save.vcdbs"),
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
        Path savePath = suiteTemporaryDirectory.resolve(
                "pf26-resource-save.vcdbs"
        );
        Files.write(savePath, new byte[]{1, 2, 3});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("pf26-resource-cache")
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
                suiteTemporaryDirectory.resolve("pf26-resource-home.properties"),
                suiteTemporaryDirectory.resolve("pf26-resource-markers.csv"),
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
        Path savePath = suiteTemporaryDirectory.resolve(
                "pf26-mapregion-save.vcdbs"
        );
        Files.write(savePath, new byte[]{4, 5, 6});
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(
                suiteTemporaryDirectory.resolve("pf26-mapregion-cache")
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
                suiteTemporaryDirectory.resolve("pf26-region-home.properties"),
                suiteTemporaryDirectory.resolve("pf26-region-markers.csv"),
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
}
