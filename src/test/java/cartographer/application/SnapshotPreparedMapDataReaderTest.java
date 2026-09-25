package cartographer.application;
import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldSnapshotHeader;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotPreparedMapDataReaderTest {
    @TempDir
    Path root;

    @Test
    void composesCompleteTerrainAndSurfaceWithoutSourceSession()
            throws Exception {
        Fixture fixture = fixture("complete", true);

        var prepared = new SnapshotPreparedMapDataReader(fixture.cache)
                .read(request(fixture.save), ProgressReporter.NONE)
                .orElseThrow();

        assertEquals(fixture.metadata, prepared.metadata());
        assertEquals(fixture.player, prepared.player());
        assertEquals(fixture.player, prepared.center());
        assertEquals(1, prepared.terrain().mapChunkCount());
        assertEquals(0, prepared.surface().diagnostics().chunksScanned());
        assertEquals(1, prepared.renderDataCacheReport().terrain().hits());
        assertEquals(1, prepared.renderDataCacheReport().surface().hits());
        assertEquals(
                0,
                prepared.renderDataCacheReport().terrain().sourceLoaded()
        );
        assertEquals(
                0,
                prepared.renderDataCacheReport().surface().sourceLoaded()
        );
        assertTrue(prepared.renderDataCacheReport().notes().stream()
                .anyMatch(note -> note.contains("source SaveSession not opened")));
    }

    @Test
    void renderSurfaceDoesNotRetainExactAnalysisMap() throws Exception {
        Fixture fixture = fixture("render-only", true);

        var prepared = new SnapshotPreparedMapDataReader(fixture.cache)
                .read(
                        request(
                                fixture.save,
                                SurfaceDataRequirement.RENDER
                        ),
                        ProgressReporter.NONE
                )
                .orElseThrow();

        assertTrue(prepared.surface().analysis().isEmpty());
        assertTrue(!prepared.surface().renderData().isEmpty());
        assertTrue(
                prepared.surface()
                        .diagnostics()
                        .columnsScanned() > 0
        );
        assertEquals(
                1,
                prepared.renderDataCacheReport().surface().hits()
        );
    }

    @Test
    void analysisSurfaceRetainsExactMapOnlyWhenRequested() throws Exception {
        Fixture fixture = fixture("analysis", true);

        var prepared = new SnapshotPreparedMapDataReader(fixture.cache)
                .read(
                        request(
                                fixture.save,
                                SurfaceDataRequirement.ANALYSIS
                        ),
                        ProgressReporter.NONE
                )
                .orElseThrow();

        assertTrue(prepared.surface().analysis().isPresent());
    }

    @Test
    void missingSurfaceCoverageDoesNotGuessAndFallsBack() throws Exception {
        Fixture fixture = fixture("surface-miss", false);

        assertTrue(new SnapshotPreparedMapDataReader(fixture.cache)
                .read(request(fixture.save), ProgressReporter.NONE)
                .isEmpty());
    }

    private Fixture fixture(String name, boolean publishSurface)
            throws Exception {
        Path save = root.resolve(name).resolve("world.vcdbs");
        Files.createDirectories(save.getParent());
        Files.write(save, new byte[]{1, 2, 3});

        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve(name + "-cache"));
        WorldDataSnapshot snapshot =
                WorldDataSnapshot.openOrCreate(cache, save).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(32, 64, 32);
        WorldPosition player = new WorldPosition(16, 20, 16);
        Map<Integer, BlockInfo> registry = Map.of(
                1,
                new BlockInfo(1, "game:rock-granite")
        );
        snapshot.headerStore().publish(
                new WorldSnapshotHeader(
                        metadata,
                        registry,
                        Optional.of(player)
                )
        );

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

        if (publishSurface) {
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

        return new Fixture(save, cache, metadata, player);
    }

    private PrepareMapDataRequest request(Path save) {
        return request(save, SurfaceDataRequirement.ANALYSIS);
    }

    private PrepareMapDataRequest request(
            Path save,
            SurfaceDataRequirement requirement
    ) {
        return new PrepareMapDataRequest(
                save,
                4,
                1,
                RenderStyle.SIMPLE,
                Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE),
                Optional.empty(),
                requirement
        );
    }

    private record Fixture(
            Path save,
            RenderDataCacheStore cache,
            WorldMetadata metadata,
            WorldPosition player
    ) {
    }
}
