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
class RenderActualOreMapLifecycleAndSurfaceTest extends RenderActualOreMapUseCaseTestSupport {

    @TempDir
    Path temporaryDirectory;

    @Override
    Path temporaryDirectory() {
        return temporaryDirectory;
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
}
