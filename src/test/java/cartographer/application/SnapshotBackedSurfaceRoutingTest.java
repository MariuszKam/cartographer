package cartographer.application;

import cartographer.resource.SurfaceMaterialMatch;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotHeader;
import cartographer.render.MapRenderer;
import cartographer.render.MapRasterContract;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceMaterialAnalyzer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotBackedSurfaceRoutingTest {
    @TempDir
    Path root;

    @Test
    void completeSurfaceSnapshotRendersWithoutOpeningSourceDatabase()
            throws Exception {
        Path save = root.resolve("save").resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{5, 4, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache"));
        prepareSnapshot(cache, save);

        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        SaveSessionFactory forbiddenSource = new SaveSessionFactory(
                new SqliteSaveConnection() {
                    @Override
                    public java.sql.Connection openReadOnly(Path ignored) {
                        throw new AssertionError(
                                "snapshot-backed Surface must not open source SQLite"
                        );
                    }
                },
                reader,
                metadataReader
        );

        RenderSurfaceResourceMapUseCase useCase =
                new RenderSurfaceResourceMapUseCase(
                        reader,
                        forbiddenSource,
                        new HomeStore(root.resolve("home.properties")),
                        new MarkerStore(root.resolve("markers.csv")),
                        new MapRenderer(),
                        new UserMarkerRenderer(),
                        new SurfaceMaterialAnalyzer(),
                        new SurfaceResourceOverlayRenderer(),
                        cache
                );

        RenderSurfaceResourceMapResult result = useCase.execute(
                new RenderSurfaceResourceMapRequest(
                        save,
                        4,
                        1,
                        RenderStyle.SIMPLE,
                        Set.of(
                                RenderLayer.TERRAIN,
                                RenderLayer.SURFACE
                        ),
                        new SurfaceMaterialMatch(
                                "Rock",
                                List.of("rock")
                        ),
                        Optional.empty()
                )
        );

        assertEquals(
                MapRasterContract.MIN_RASTER_SIZE,
                result.geometry().imageWidth()
        );
        assertEquals(
                MapRasterContract.MIN_RASTER_SIZE,
                result.geometry().imageHeight()
        );
        assertEquals(8.0, result.geometry().worldWidthBlocks());
        assertEquals(8.0, result.geometry().worldHeightBlocks());
        assertEquals(0, result.surface().chunksScanned());
        assertTrue(result.renderDataCacheReport().notes().stream()
                .anyMatch(note -> note.contains(
                        "source SaveSession not opened"
                )));
    }

    private void prepareSnapshot(
            RenderDataCacheStore cache,
            Path save
    ) {
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(32, 64, 32);
        WorldPosition player = new WorldPosition(16, 20, 16);
        Map<Integer, BlockInfo> registry = Map.of(
                1,
                new BlockInfo(1, "game:rock-granite")
        );
        snapshot.headerStore().publish(new WorldSnapshotHeader(
                metadata,
                registry,
                Optional.of(player)
        ));

        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        snapshot.indexCatalogStore().recordObserved(Set.of(coordinate));
        snapshot.indexCatalogStore().markMapChunkScanComplete();

        int[] heights = new int[32 * 32];
        Arrays.fill(heights, 10);
        snapshot.terrainStore().publish(Set.of(
                new TerrainHeightTile(
                        coordinate,
                        true,
                        true,
                        heights
                )
        ));

        int cells = 32 * 32;
        byte[] state = new byte[cells];
        Arrays.fill(
                state,
                (byte) (
                        SurfaceCacheTile.CONSIDERED
                                | SurfaceCacheTile.RESOLVED
                )
        );
        int[] surfaceY = new int[cells];
        Arrays.fill(surfaceY, 10);
        int[] blockIds = new int[cells];
        Arrays.fill(blockIds, 1);
        int[] liquidIds = new int[cells];
        byte[] classes = new byte[cells];
        Arrays.fill(
                classes,
                SurfaceClassCode.encode(SurfaceClass.ROCK)
        );
        snapshot.surfaceStore().publish(Set.of(
                new SurfaceCacheTile(
                        coordinate,
                        32,
                        32,
                        state,
                        surfaceY,
                        blockIds,
                        liquidIds,
                        classes,
                        SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                        cells,
                        0,
                        0
                )
        ));
    }
}
