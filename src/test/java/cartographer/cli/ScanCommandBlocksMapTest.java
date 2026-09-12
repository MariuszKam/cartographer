package cartographer.cli;

import cartographer.analysis.BlockScanner;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualBlockMapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.SurfaceScanner;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

        PrintStream out =
                new PrintStream(
                        buffer,
                        true,
                        StandardCharsets.UTF_8
                );

        ScanCommand command =
                new ScanCommand(
                        out,
                        reader,
                        new SurfaceScanner(),
                        new BlockScanner(),
                        new ActualBlockMapScanner(),
                        new ActualBlockMapRenderer(),
                        writer,
                        "blocks-map"
                );

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
    void rejectsInvertedYFilter() {
        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        PrintStream out =
                new PrintStream(
                        buffer,
                        true,
                        StandardCharsets.UTF_8
                );

        ScanCommand command =
                new ScanCommand(
                        out,
                        new FakeReader(),
                        new SurfaceScanner(),
                        new BlockScanner(),
                        new ActualBlockMapScanner(),
                        new ActualBlockMapRenderer(),
                        new CapturingPngWriter(),
                        "blocks-map"
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

    private static final class CapturingPngWriter
            extends PngWriter {

        private BufferedImage image;
        private Path output;

        @Override
        public void write(
                BufferedImage image,
                Path output
        ) {
            this.image =
                    image;

            this.output =
                    output;
        }
    }

    private static final class FakeReader
            extends VcdbsReader {

        private WorldPosition lastCenter;
        private int lastRadiusBlocks;

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
                Path savePath,
                ProgressReporter progress
        ) {
            return new WorldPosition(
                    0.0,
                    100.0,
                    0.0
            );
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            lastCenter =
                    center;

            lastRadiusBlocks =
                    radiusBlocks;

            return List.of(
                    copperChunk()
            );
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(
                Path savePath,
                ProgressReporter progress
        ) {
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
}
