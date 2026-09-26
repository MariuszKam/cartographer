package cartographer.save;

import cartographer.testing.IntegrationTest;
import cartographer.progress.ProgressReporter;
import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import cartographer.parser.MapChunkParser;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class VcdbsReaderSessionConnectionOwnershipTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sessionAwareReaderBorrowsConnectionUntilOwningSessionCloses() throws Exception {
        Path save = createSave();
        MapChunkCoordinate coordinate = new MapChunkCoordinate(1, 2);
        try (Connection connection = new SqliteSaveConnection().openReadOnly(save)) {
            SaveSession session = new SaveSession(
                    save,
                    connection,
                    new SaveSnapshot(new WorldMetadata(128, 256, 128), Map.of())
            );
            VcdbsReader reader = VcdbsReaderFixtures.withMapChunkParser(new FixtureMapChunkParser());
            List<MapChunk> first = new ArrayList<>();
            List<MapChunk> second = new ArrayList<>();

            reader.forEachMapChunkByCoordinate(
                    session, List.of(coordinate), new ReadDiagnostics(), first::add,
                    ProgressReporter.NONE
            );
            assertFalse(connection.isClosed());
            reader.forEachMapChunkByCoordinate(
                    session, List.of(coordinate), new ReadDiagnostics(), second::add,
                    ProgressReporter.NONE
            );
            assertFalse(connection.isClosed());
            assertEquals(1, first.size());
            assertEquals(1, second.size());

            session.close();
            assertTrue(connection.isClosed());
            session.close();
            assertTrue(connection.isClosed());
        }
    }

    private Path createSave() throws Exception {
        Path save = temporaryDirectory.resolve("session-reader.vcdbs");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + save);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE mapchunk (position INTEGER PRIMARY KEY, data BLOB)");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + save);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO mapchunk(position, data) VALUES (?, ?)")) {
            statement.setLong(1, ChunkPosEncoder.encode(1, 0, 2, 0));
            statement.setBytes(2, new byte[]{1});
            statement.executeUpdate();
        }
        return save;
    }

    private static final class FixtureMapChunkParser extends MapChunkParser {
        @Override
        public ParseResult<MapChunk> parse(MapChunkCoordinate coordinate, byte[] payload) {
            return ParseResult.success(new MapChunk(
                    coordinate,
                    new int[MapChunk.HEIGHT_VALUE_COUNT],
                    new int[0]
            ));
        }
    }
}
