package cartographer.cli;

import cartographer.analysis.BlockScanner;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualBlockMapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanCommandBlocksMapTest {

    @Test
    void rendersActualBlockMapThroughCliCommand() {
        FakeReader reader =
                new FakeReader();

        CapturingPngWriter writer =
                new CapturingPngWriter();

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        ScanCommand command = blocksMapCommand(buffer, reader, writer);

        command.run(
                new String[]{
                        "save.vcdbs",
                        "--match",
                        "copper",
                        "--radius",
                        "8",
                        "--y-min",
                        "5",
                        "--y-max",
                        "5",
                        "--scale",
                        "2",
                        "--out",
                        "copper-actual.png"
                }
        );

        assertEquals(
                Path.of(
                        "copper-actual.png"
                ),
                writer.output
        );

        assertNotNull(
                writer.image
        );

        assertEquals(
                8,
                reader.lastRadiusBlocks
        );

        assertEquals(
                0.0,
                reader.lastCenter.x()
        );

        assertEquals(
                0.0,
                reader.lastCenter.z()
        );

        String output =
                buffer.toString(
                        StandardCharsets.UTF_8
                );

        assertTrue(
                output.contains(
                        "ACTUAL BLOCK MAP"
                )
        );

        assertTrue(
                output.contains(
                        "Match: copper"
                )
        );

        assertTrue(
                output.contains(
                        "Output: copper-actual.png"
                )
        );

        assertTrue(
                output.contains(
                        "Center: 0,0"
                )
        );

        assertTrue(
                output.contains(
                        "Radius: 8"
                )
        );

        assertTrue(
                output.contains(
                        "Scale: 2"
                )
        );

        assertTrue(
                output.contains(
                        "Y filter: 5..5"
                )
        );

        assertTrue(
                output.contains(
                        "Matching blocks: 1"
                )
        );

        assertTrue(
                output.contains(
                        "Hit columns: 1"
                )
        );

        assertTrue(
                output.contains(
                        "Y range: 5..5"
                )
        );
    }

    @Test
    void rendersSplitYBandsWithoutRereadingSave() {
        FakeReader reader =
                new FakeReader();

        CapturingPngWriter writer =
                new CapturingPngWriter();

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        ScanCommand command = blocksMapCommand(buffer, reader, writer);

        command.run(
                new String[]{
                        "save.vcdbs",
                        "--match",
                        "copper",
                        "--radius",
                        "8",
                        "--split-y",
                        "16",
                        "--out-dir",
                        "output/copper-bands"
                }
        );

        assertEquals(
                1,
                reader.chunkReads
        );

        assertEquals(
                1,
                reader.registryReads
        );

        assertEquals(
                List.of(
                        Path.of(
                                "output/copper-bands/copper-y000-015.png"
                        ),
                        Path.of(
                                "output/copper-bands/copper-y016-031.png"
                        ),
                        Path.of(
                                "output/copper-bands/copper-y032-047.png"
                        ),
                        Path.of(
                                "output/copper-bands/copper-y048-063.png"
                        )
                ),
                writer.outputs
        );

        String output =
                buffer.toString(
                        StandardCharsets.UTF_8
                );

        assertTrue(
                output.contains(
                        "ACTUAL BLOCK MAP BANDS"
                )
        );

        assertTrue(
                output.contains(
                        "Band 0..15: output=output\\copper-bands\\copper-y000-015.png matchingBlocks=2 hitColumns=1 yRange=4..5"
                )
                        || output.contains(
                        "Band 0..15: output=output/copper-bands/copper-y000-015.png matchingBlocks=2 hitColumns=1 yRange=4..5"
                )
        );

        assertTrue(
                output.contains(
                        "Band 16..31: output=output\\copper-bands\\copper-y016-031.png matchingBlocks=1 hitColumns=1 yRange=20..20"
                )
                        || output.contains(
                        "Band 16..31: output=output/copper-bands/copper-y016-031.png matchingBlocks=1 hitColumns=1 yRange=20..20"
                )
        );

        assertTrue(
                output.contains(
                        "Band 32..47: output=output\\copper-bands\\copper-y032-047.png matchingBlocks=1 hitColumns=1 yRange=40..40"
                )
                        || output.contains(
                        "Band 32..47: output=output/copper-bands/copper-y032-047.png matchingBlocks=1 hitColumns=1 yRange=40..40"
                )
        );

        assertTrue(
                output.contains(
                        "Band 48..63: output=output\\copper-bands\\copper-y048-063.png matchingBlocks=0 hitColumns=0 yRange=none"
                )
                        || output.contains(
                        "Band 48..63: output=output/copper-bands/copper-y048-063.png matchingBlocks=0 hitColumns=0 yRange=none"
                )
        );
    }

    @Test
    void rendersSplitYBandsInsideExplicitYBounds() {
        FakeReader reader =
                new FakeReader();

        CapturingPngWriter writer =
                new CapturingPngWriter();

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        ScanCommand command = blocksMapCommand(buffer, reader, writer);

        command.run(
                new String[]{
                        "save.vcdbs",
                        "--match",
                        "copper",
                        "--radius",
                        "8",
                        "--y-min",
                        "5",
                        "--y-max",
                        "36",
                        "--split-y",
                        "16",
                        "--out-dir",
                        "output/copper-bands"
                }
        );

        assertEquals(
                List.of(
                        Path.of(
                                "output/copper-bands/copper-y005-020.png"
                        ),
                        Path.of(
                                "output/copper-bands/copper-y021-036.png"
                        )
                ),
                writer.outputs
        );

        String output =
                buffer.toString(
                        StandardCharsets.UTF_8
                );

        assertTrue(
                output.contains(
                        "Y filter: 5..36"
                )
        );
    }

    @Test
    void rejectsOutFileWhenSplittingYBands() {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();
        ScanCommand command = blocksMapCommand(
                buffer,
                new FakeReader(),
                new CapturingPngWriter()
        );

        CommandException exception =
                assertThrows(
                        CommandException.class,
                        () ->
                                command.run(
                                        new String[]{
                                                "save.vcdbs",
                                                "--match",
                                                "copper",
                                                "--split-y",
                                                "16",
                                                "--out",
                                                "single.png"
                                        }
                                )
                );

        assertEquals(
                "--out cannot be used with --split-y; use --out-dir",
                exception.getMessage()
        );
    }

    @Test
    void rejectsInvertedYFilter() {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();
        ScanCommand command = blocksMapCommand(
                buffer,
                new FakeReader(),
                new CapturingPngWriter()
        );

        CommandException exception =
                assertThrows(
                        CommandException.class,
                        () ->
                                command.run(
                                        new String[]{
                                                "save.vcdbs",
                                                "--match",
                                                "copper",
                                                "--y-min",
                                                "64",
                                                "--y-max",
                                                "32"
                                        }
                                )
                );

        assertEquals(
                "--y-min must not be greater than --y-max",
                exception.getMessage()
        );
    }

    private static ParsedChunk copperChunk() {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        int[] blocks =
                new int[
                        size
                                * size
                                * size
                        ];

        Arrays.fill(
                blocks,
                2
        );

        blocks[
                index(
                        0,
                        4,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        0,
                        5,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        0,
                        20,
                        0
                )
                ] =
                1;

        blocks[
                index(
                        10,
                        5,
                        0
                )
                ] =
                1;

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        0,
                        0
                ),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private static List<ParsedChunk> copperChunks() {
        return List.of(
                copperChunk(),
                copperChunk(
                        1,
                        8
                )
        );
    }

    private static ParsedChunk copperChunk(
            int chunkY,
            int localY
    ) {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        int[] blocks =
                new int[
                        size
                                * size
                                * size
                        ];

        Arrays.fill(
                blocks,
                2
        );

        blocks[
                index(
                        0,
                        localY,
                        0
                )
                ] =
                1;

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        chunkY,
                        0
                ),
                chunkY
                        * size,
                size,
                size,
                size,
                blocks
        );
    }

    private static int index(
            int x,
            int y,
            int z
    ) {
        int size =
                ChunkCoordinate.SIZE_BLOCKS;

        return (y * size + z)
                * size
                + x;
    }

    private ScanCommand blocksMapCommand(
            ByteArrayOutputStream buffer,
            FakeReader reader,
            CapturingPngWriter writer
    ) {
        PrintStream out =
                new PrintStream(
                        buffer,
                        true,
                        StandardCharsets.UTF_8
                );
        return new ScanCommand(
                out,
                reader,
                sessionFactory(
                        reader
                ),
                new BlockScanner(),
                new ActualBlockMapScanner(),
                new ActualBlockMapRenderer(),
                writer,
                "blocks-map"
        );
    }

    private static SaveSessionFactory sessionFactory(
            FakeReader reader
    ) {
        return new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                new FakeMetadataReader()
        );
    }

    private static final class CapturingPngWriter
            extends PngWriter {

        private BufferedImage image;
        private Path output;
        private final List<Path> outputs =
                new ArrayList<>();

        @Override
        public void write(
                BufferedImage image,
                Path output
        ) {
            this.image =
                    image;

            this.output =
                    output;

            outputs.add(
                    output
            );
        }
    }

    private static final class FakeReader
            extends VcdbsReader {

        private WorldPosition lastCenter;
        private int lastRadiusBlocks;
        private int chunkReads;
        private int registryReads;

        private FakeReader() {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                cartographer.application.ProgressReporter progress
        ) {
            return new WorldPosition(
                    0.0,
                    100.0,
                    0.0
            );
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                SaveSession session,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
        ) {
            lastCenter =
                    center;

            lastRadiusBlocks =
                    radiusBlocks;

            chunkReads++;

            return copperChunks();
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            registryReads++;

            return Map.of(
                    1,
                    new BlockInfo(
                            1,
                            "ore-poor-nativecopper-granite"
                    ),
                    2,
                    new BlockInfo(
                            2,
                            "rock-granite"
                    )
            );
        }
    }
    private static final class FakeMetadataReader
            extends WorldMetadataReader {

        @Override
        protected WorldMetadata read(
                Connection connection,
                cartographer.application.ProgressReporter progress
        ) {
            return new WorldMetadata(
                    1024,
                    256,
                    1024
            );
        }
    }

    private static final class TestConnectionFactory
            extends SqliteSaveConnection {

        @Override
        public Connection openReadOnly(
                Path savePath
        ) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> null
            );
        }
    }
}
