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

class GeologyCommandPlayerCenteredSectionTest {

    @Test
    void defaultsToEastWestSectionCenteredOnPlayer() {
        FakeReader reader =
                new FakeReader(
                        new WorldPosition(
                                10.2,
                                111.0,
                                20.7
                        )
                );

        CapturingPngWriter writer =
                new CapturingPngWriter();

        String output =
                runCommand(
                        reader,
                        writer,
                        "save.vcdbs"
                );

        assertNotNull(
                writer.image
        );

        assertEquals(
                532,
                reader.lastRadiusBlocks
        );

        assertEquals(
                10.0,
                reader.lastCenter.x()
        );

        assertEquals(
                21.0,
                reader.lastCenter.z()
        );

        assertTrue(
                output.contains(
                        "Mode: PLAYER_CENTERED"
                )
        );

        assertTrue(
                output.contains(
                        "Player world: 10.200,111.000,20.700"
                )
        );

        assertTrue(
                output.contains(
                        "Axis: EAST_WEST"
                )
        );

        assertTrue(
                output.contains(
                        "Radius: 500 blocks"
                )
        );

        assertTrue(
                output.contains(
                        "From world: -490,21"
                )
        );

        assertTrue(
                output.contains(
                        "To world: 510,21"
                )
        );

        assertTrue(
                output.contains(
                        "Columns: 1001"
                )
        );

        assertTrue(
                output.contains(
                        "Player marker: column 500 at Y 111"
                )
        );
    }

    @Test
    void acceptsNorthSouthAxisAndCustomRadius() {
        FakeReader reader =
                new FakeReader(
                        new WorldPosition(
                                10.2,
                                111.0,
                                20.7
                        )
                );

        CapturingPngWriter writer =
                new CapturingPngWriter();

        String output =
                runCommand(
                        reader,
                        writer,
                        "save.vcdbs",
                        "--axis",
                        "north-south",
                        "--radius",
                        "750"
                );

        assertNotNull(
                writer.image
        );

        assertEquals(
                782,
                reader.lastRadiusBlocks
        );

        assertEquals(
                10.0,
                reader.lastCenter.x()
        );

        assertEquals(
                21.0,
                reader.lastCenter.z()
        );

        assertTrue(
                output.contains(
                        "Axis: NORTH_SOUTH"
                )
        );

        assertTrue(
                output.contains(
                        "Radius: 750 blocks"
                )
        );

        assertTrue(
                output.contains(
                        "From world: 10,-729"
                )
        );

        assertTrue(
                output.contains(
                        "To world: 10,771"
                )
        );

        assertTrue(
                output.contains(
                        "Columns: 1501"
                )
        );

        assertTrue(
                output.contains(
                        "Player marker: column 750 at Y 111"
                )
        );
    }

    private String runCommand(
            FakeReader reader,
            CapturingPngWriter writer,
            String... args
    ) {
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
                        new GeologyAnalyzer(),
                        new GeologyCrossSectionAnalyzer(),
                        new GeologyCrossSectionRenderer(),
                        writer,
                        "section"
                );

        command.run(
                args
        );

        return buffer.toString(
                StandardCharsets.UTF_8
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

        @Override
        public void write(
                BufferedImage image,
                Path output
        ) {
            this.image =
                    image;

        }
    }

    private static final class FakeReader
            extends VcdbsReader {

        private final WorldPosition player;
        private WorldPosition lastCenter;
        private int lastRadiusBlocks;

        private FakeReader(
                WorldPosition player
        ) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );

            this.player =
                    player;
        }

        @Override
        public WorldPosition readPlayerPosition(
                Path savePath,
                cartographer.application.ProgressReporter progress
        ) {
            return player;
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
        ) {
            lastCenter =
                    center;

            lastRadiusBlocks =
                    radiusBlocks;

            return List.of(
                    graniteChunk()
            );
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(
                Path savePath,
                cartographer.application.ProgressReporter progress
        ) {
            return Map.of(
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
            );
        }
    }
}
