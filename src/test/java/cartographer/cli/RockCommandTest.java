package cartographer.cli;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockCommandTest {
    @Test
    void listsOnlyRegistryDrivenNaturalRocks() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FakeReader reader = new FakeReader();
        reader.registry = Map.of(
                7, new BlockInfo(7, "somemod:rock-gneiss"),
                8, new BlockInfo(8, "game:ore-cassiterite-granite")
        );

        new RockCommand(
                new PrintStream(output),
                reader,
                null,
                new PngWriter(),
                "list",
                new SaveSessionFactory(
                        new TestConnectionFactory(),
                        reader,
                        new FakeMetadataReader()
                )
        ).run(new String[]{"world.vcdbs"});

        String text = output.toString();
        assertTrue(text.contains("somemod:rock-gneiss"));
        assertTrue(!text.contains("ore-cassiterite"));
    }

    @Test
    void rendersObservedSavedGeologyThroughSelectiveReader() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CapturingPngWriter pngWriter = new CapturingPngWriter();
        FakeReader reader = new FakeReader();
        reader.registry = Map.of(
                7, new BlockInfo(7, "somemod:rock-gneiss")
        );

        new RockCommand(
                new PrintStream(output),
                reader,
                new cartographer.render.RockMapRenderer(),
                pngWriter,
                "render",
                new SaveSessionFactory(
                        new TestConnectionFactory(), reader, new FakeMetadataReader()
                )
        ).run(new String[]{
                "world.vcdbs",
                "--center-x", "1",
                "--center-z", "1",
                "--radius", "1",
                "--min-y", "0",
                "--max-y", "32"
        });

        assertEquals(3, pngWriter.image.getWidth());
        assertTrue(output.toString().contains("Observed saved geology"));
        assertTrue(output.toString().contains("somemod:rock-gneiss"));
    }

    @Test
    void atYModeRequiresY() {
        FakeReader reader = new FakeReader();
        FakeMetadataReader metadataReader = new FakeMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                metadataReader
        );

        assertThrows(
                CommandException.class,
                () -> new RockCommand(
                        new PrintStream(new ByteArrayOutputStream()),
                        reader,
                        new cartographer.render.RockMapRenderer(),
                        new PngWriter(),
                        "render",
                        sessionFactory
                ).run(new String[]{
                        "world.vcdbs",
                        "--mode", "at-y",
                        "--center-x", "1",
                        "--center-z", "1"
                })
        );
    }

    private static final class FakeReader extends VcdbsReader {
        private Map<Integer, BlockInfo> registry = Map.of();

        private FakeReader() {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            return registry;
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            blocks[(1 * size + 1) * size + 1] = 7;
            ParsedChunk chunk = cartographer.model.ParsedChunkFixtures.create(
                    new ChunkCoordinate(0, 0, 0),
                    0,
                    size,
                    size,
                    size,
                    blocks
            );
            consumer.accept(
                    SelectiveChunkVisit.decoded(
                            new ChunkPosition(0, 0, 0, 0),
                            chunk
                    )
            );
            return new SelectiveChunkStreamStats(
                    positions.size(), 1, 1, 1, 0, 1, 0, 1
            );
        }
    }

    private static final class FakeMetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(
                Connection connection,
                cartographer.application.ProgressReporter progress
        ) {
            return new WorldMetadata(32, 32, 32);
        }
    }

    private static final class CapturingPngWriter extends PngWriter {
        private BufferedImage image;

        @Override
        public void write(BufferedImage image, Path output) {
            this.image = image;
        }
    }

    private static final class TestConnectionFactory extends SqliteSaveConnection {
        @Override
        public Connection openReadOnly(Path savePath) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> null
            );
        }
    }
}
