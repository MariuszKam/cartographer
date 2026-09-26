package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.progress.ProgressReporter;
import cartographer.render.RenderTileBounds;
import cartographer.save.ChunkPosEncoder;
import cartographer.save.MapChunkReadStatus;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSnapshot;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.WorldDataSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileDataLoaderTest {

    @TempDir
    Path root;

    @Test
    void exactFallbackPublishesTerrainAndSecondLoadIsCacheHit()
            throws Exception {
        Path save = sourceWithRow(new MapChunkCoordinate(0, 0), false);
        CountingParser parser = new CountingParser();
        VcdbsReader reader = reader(parser);
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-a"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(32, 256, 32);
        MapTileDataLoader loader =
                new MapTileDataLoader(reader, new WorldIndexBatchPlanner(4));

        try (SaveSession session = session(save, metadata)) {
            MapTileData first = loader.load(
                    session,
                    metadata,
                    Map.of(),
                    snapshot.terrainStore(),
                    snapshot.surfaceStore(),
                    new RenderTileBounds(0, 0, 32, 32),
                    MapTileDataRequirement.TERRAIN,
                    new ReadDiagnostics(),
                    ProgressReporter.NONE
            );

            assertTrue(first.terrainComplete());
            assertEquals(
                    MapChunkReadStatus.PRESENT_DECODED,
                    first.sourceStatuses().get(
                            new MapChunkCoordinate(0, 0)
                    )
            );
            assertEquals(1, parser.calls.get());

            MapTileData second = loader.load(
                    session,
                    metadata,
                    Map.of(),
                    snapshot.terrainStore(),
                    snapshot.surfaceStore(),
                    new RenderTileBounds(0, 0, 32, 32),
                    MapTileDataRequirement.TERRAIN,
                    new ReadDiagnostics(),
                    ProgressReporter.NONE
            );

            assertTrue(second.terrainComplete());
            assertTrue(second.sourceStatuses().isEmpty());
            assertEquals(1, parser.calls.get());
        }
    }

    @Test
    void preservesUnreadableAndAbsentSourceStates() throws Exception {
        MapChunkCoordinate unreadable = new MapChunkCoordinate(0, 0);
        MapChunkCoordinate absent = new MapChunkCoordinate(1, 0);
        Path save = sourceWithRow(unreadable, true);
        VcdbsReader reader = reader(new CountingParser());
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-b"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(64, 256, 32);

        try (SaveSession session = session(save, metadata)) {
            MapTileData data = new MapTileDataLoader(
                    reader,
                    new WorldIndexBatchPlanner(4)
            ).load(
                    session,
                    metadata,
                    Map.of(),
                    snapshot.terrainStore(),
                    snapshot.surfaceStore(),
                    new RenderTileBounds(0, 0, 64, 32),
                    MapTileDataRequirement.TERRAIN,
                    new ReadDiagnostics(),
                    ProgressReporter.NONE
            );

            assertFalse(data.terrainComplete());
            assertEquals(
                    MapChunkReadStatus.PRESENT_UNREADABLE,
                    data.sourceStatuses().get(unreadable)
            );
            assertEquals(
                    MapChunkReadStatus.ABSENT,
                    data.sourceStatuses().get(absent)
            );
        }
    }

    @Test
    void reusesCompatibleCachedSurfaceWithoutSourceSurfaceScan()
            throws Exception {
        MapChunkCoordinate coordinate = new MapChunkCoordinate(0, 0);
        Path save = sourceWithRow(coordinate, false);
        RenderDataCacheStore cache =
                new RenderDataCacheStore(root.resolve("cache-c"));
        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cache,
                save
        ).orElseThrow();
        WorldMetadata metadata = new WorldMetadata(32, 256, 32);

        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        snapshot.terrainStore().publish(
                java.util.List.of(
                        new TerrainHeightTile(
                                coordinate,
                                true,
                                true,
                                heights
                        )
                )
        );
        byte[] classes = new byte[MapChunk.HEIGHT_VALUE_COUNT];
        Arrays.fill(
                classes,
                SurfaceClassCode.encode(SurfaceClass.UNKNOWN)
        );
        snapshot.surfaceStore().publish(
                java.util.List.of(
                        new SurfaceCacheTile(
                                coordinate,
                                32,
                                32,
                                new byte[MapChunk.HEIGHT_VALUE_COUNT],
                                new int[MapChunk.HEIGHT_VALUE_COUNT],
                                new int[MapChunk.HEIGHT_VALUE_COUNT],
                                new int[MapChunk.HEIGHT_VALUE_COUNT],
                                classes,
                                SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                                0,
                                0,
                                0
                        )
                )
        );

        CountingParser parser = new CountingParser();
        try (SaveSession session = session(save, metadata)) {
            MapTileData data = new MapTileDataLoader(
                    reader(parser),
                    new WorldIndexBatchPlanner(4)
            ).load(
                    session,
                    metadata,
                    Map.of(),
                    snapshot.terrainStore(),
                    snapshot.surfaceStore(),
                    new RenderTileBounds(0, 0, 32, 32),
                    MapTileDataRequirement.TERRAIN_AND_SURFACE,
                    new ReadDiagnostics(),
                    ProgressReporter.NONE
            );

            assertTrue(data.completeForRequirement());
            assertEquals(1, data.surfaceTiles().size());
            assertEquals(0, parser.calls.get());
            assertTrue(data.sourceStatuses().isEmpty());
        }
    }

    private Path sourceWithRow(
            MapChunkCoordinate coordinate,
            boolean nullPayload
    ) throws Exception {
        Path save = root.resolve(
                "save-" + System.nanoTime() + ".vcdbs"
        );
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + save
        );
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapchunk "
                            + "(position INTEGER PRIMARY KEY, data BLOB)"
            );
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + save
        );
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)"
             )) {
            statement.setLong(
                    1,
                    ChunkPosEncoder.encode(
                            coordinate.x(),
                            0,
                            coordinate.z(),
                            0
                    )
            );
            if (nullPayload) {
                statement.setBytes(2, null);
            } else {
                statement.setBytes(2, new byte[]{1});
            }
            statement.executeUpdate();
        }
        return save;
    }

    private SaveSession session(
            Path save,
            WorldMetadata metadata
    ) {
        return new SaveSession(
                save,
                new SqliteSaveConnection().openReadOnly(save),
                new SaveSnapshot(metadata, Map.of())
        );
    }

    private VcdbsReader reader(MapChunkParser parser) {
        return new VcdbsReader(
                new PlayerDataParser(),
                parser,
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private static final class CountingParser extends MapChunkParser {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ParseResult<MapChunk> parse(
                MapChunkCoordinate coordinate,
                byte[] payload
        ) {
            calls.incrementAndGet();
            int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
            Arrays.fill(heights, 100);
            return ParseResult.success(
                    new MapChunk(
                            coordinate,
                            heights,
                            heights
                    )
            );
        }
    }
}
