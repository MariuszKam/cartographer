package cartographer.cli;

import cartographer.geology.GeologyAnalyzer;
import cartographer.geology.crosssection.GeologyCrossSectionAnalyzer;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.GeologyCrossSectionRenderer;
import cartographer.render.PngWriter;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeologyCommandSectionTest {

    @Test
    void rendersSectionThroughCliCommand() {
        FakeReader reader =
                new FakeReader(
                        List.of(
                                graniteChunk()
                        ),
                        Map.of(
                                0,
                                new BlockInfo(
                                        0,
                                        "air"
                                ),
                                1,
                                new BlockInfo(
                                        1,
                                        "rock-granite"
                                )
                        )
                );

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

        GeologyCommand command =
                new GeologyCommand(
                        out,
                        reader,
                        new SurfaceScanner(),
                        new GeologyAnalyzer(),
                        new GeologyCrossSectionAnalyzer(),
                        new GeologyCrossSectionRenderer(),
                        writer,
                        "section"
                );

        command.run(
                new String[]{
                        "save.vcdbs",
                        "--from-x",
                        "0",
                        "--from-z",
                        "0",
                        "--to-x",
                        "1",
                        "--to-z",
                        "0",
                        "--out",
                        "section.png"
                }
        );

        assertEquals(
                Path.of(
                        "section.png"
                ),
                writer.output
        );

        assertNotNull(
                writer.image
        );

        String output =
                buffer.toString(
                        StandardCharsets.UTF_8
                );

        assertTrue(
                output.contains(
                        "GEOLOGY SECTION"
                )
        );

        assertTrue(
                output.contains(
                        "Columns: 2"
                )
        );

        assertTrue(
                output.contains(
                        "Observed block samples: 64"
                )
        );

        assertTrue(
                output.contains(
                        "Chunks failed: 0"
                )
        );
    }

    private static ParsedChunk graniteChunk() {
        int[] blocks =
                new int[
                        ChunkCoordinate.SIZE_BLOCKS
                                * ChunkCoordinate.SIZE_BLOCKS
                                * ChunkCoordinate.SIZE_BLOCKS
                        ];

        Arrays.fill(
                blocks,
                1
        );

        return new ParsedChunk(
                new ChunkCoordinate(
                        0,
                        0,
                        0
                ),
                0,
                ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS,
                ChunkCoordinate.SIZE_BLOCKS,
                blocks
        );
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

        private final List<ParsedChunk> chunks;
        private final Map<Integer, BlockInfo> registry;

        private FakeReader(
                List<ParsedChunk> chunks,
                Map<Integer, BlockInfo> registry
        ) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );

            this.chunks =
                    chunks;

            this.registry =
                    registry;
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            return chunks;
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(
                Path savePath,
                ProgressReporter progress
        ) {
            return registry;
        }
    }
}
