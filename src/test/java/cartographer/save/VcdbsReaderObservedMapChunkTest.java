package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.application.ProgressReporter;
import cartographer.cli.CommandException;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@IntegrationTest
class VcdbsReaderObservedMapChunkTest {

    @TempDir
    Path root;

    @Test
    void fullDiscoveryStreamsOnlyMainWorldYZeroMapChunks() throws Exception {
        Path database = root.resolve("world.vcdbs");
        try (Connection setup = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); Statement statement = setup.createStatement()) {
            statement.execute(
                    "CREATE TABLE mapchunk (position INTEGER PRIMARY KEY, data BLOB)"
            );
            try (PreparedStatement insert = setup.prepareStatement(
                    "INSERT INTO mapchunk(position, data) VALUES (?, ?)"
            )) {
                insert(insert, new cartographer.model.ChunkPosition(2, 0, 3, 0));
                insert(insert, new cartographer.model.ChunkPosition(4, 1, 5, 0));
                insert(insert, new cartographer.model.ChunkPosition(6, 0, 7, 1));
                insert(insert, new cartographer.model.ChunkPosition(8, 0, 9, 0));
                insert(insert, new cartographer.model.ChunkPosition(40, 0, 1, 0));
            }
        }

        StubMapChunkParser parser = new StubMapChunkParser();
        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(),
                parser,
                new ChunkParser(),
                new RegistryParser()
        );
        List<MapChunkCoordinate> observed = new ArrayList<>();
        List<MapChunkCoordinate> delivered = new ArrayList<>();
        ReadDiagnostics diagnostics = new ReadDiagnostics();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); SaveSession session = new SaveSession(
                database,
                connection,
                new SaveSnapshot(
                        database,
                        new WorldMetadata(1024, 256, 1024),
                        Map.of()
                )
        )) {
            MapChunkStreamStats stats = reader.forEachObservedMapChunk(
                    session,
                    diagnostics,
                    observed::add,
                    mapChunk -> delivered.add(mapChunk.coordinate()),
                    ProgressReporter.NONE
            );

            assertEquals(5, stats.rowsFound());
            assertEquals(1, stats.parsedMapChunks());
            assertEquals(1, stats.failedMapChunks());
            assertEquals(
                    List.of(
                            new MapChunkCoordinate(2, 3),
                            new MapChunkCoordinate(8, 9)
                    ),
                    observed
            );
            assertEquals(
                    List.of(new MapChunkCoordinate(2, 3)),
                    delivered
            );
        }
    }

    @Test
    void missingMapchunkTableCannotBeMarkedAsCompleteDiscovery() throws Exception {
        Path database = root.resolve("missing-mapchunk.vcdbs");
        try (Connection ignored = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        )) {
            // Intentionally empty database.
        }

        VcdbsReader reader = new VcdbsReader(
                new PlayerDataParser(),
                new StubMapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        ); SaveSession session = new SaveSession(
                database,
                connection,
                new SaveSnapshot(
                        database,
                        new WorldMetadata(1024, 256, 1024),
                        Map.of()
                )
        )) {
            assertThrows(
                    CommandException.class,
                    () -> reader.forEachObservedMapChunk(
                            session,
                            new ReadDiagnostics(),
                            ignoredCoordinate -> { },
                            ignoredMapChunk -> { },
                            ProgressReporter.NONE
                    )
            );
        }
    }

    private static void insert(
            PreparedStatement statement,
            cartographer.model.ChunkPosition position
    ) throws Exception {
        statement.setLong(1, ChunkPosEncoder.encode(position));
        statement.setBytes(2, new byte[]{1});
        statement.executeUpdate();
    }

    private static final class StubMapChunkParser extends MapChunkParser {
        @Override
        public ParseResult<MapChunk> parse(
                MapChunkCoordinate coordinate,
                byte[] payload
        ) {
            if (coordinate.equals(new MapChunkCoordinate(8, 9))) {
                return ParseResult.failure("synthetic parser failure");
            }
            return ParseResult.success(new MapChunk(
                    coordinate,
                    new int[MapChunk.HEIGHT_VALUE_COUNT],
                    new int[MapChunk.HEIGHT_VALUE_COUNT]
            ));
        }
    }
}
