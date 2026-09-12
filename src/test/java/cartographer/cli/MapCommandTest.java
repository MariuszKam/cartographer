package cartographer.cli;

import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.MapRenderReport;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.render.RenderOptions;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPerSaveHomeAndConvertsDisplayHomeToAbsoluteBeforeRendering() {
        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        HomeStore homeStore =
                new HomeStore(
                        tempDir.resolve(
                                "home.properties"
                        )
                );

        MarkerStore markerStore =
                new MarkerStore(
                        tempDir.resolve(
                                "markers.csv"
                        )
                );

        homeStore.save(
                savePath,
                new HomeLocation(
                        -10.0,
                        20.0
                )
        );

        CapturingRenderer renderer =
                new CapturingRenderer();

        MapCommand command =
                new MapCommand(
                        new PrintStream(
                                new ByteArrayOutputStream()
                        ),
                        new FakeReader(),
                        new FakeMetadataReader(),
                        homeStore,
                        markerStore,
                        renderer,
                        new UserMarkerRenderer(),
                        new NoopPngWriter(),
                        "render"
                );

        command.run(
                new String[]{
                        savePath.toString(),
                        "--radius",
                        "64",
                        "--out",
                        tempDir.resolve(
                                "map.png"
                        ).toString()
                }
        );

        assertEquals(
                new HomeLocation(
                        502.0,
                        532.0
                ),
                renderer.homeLocation()
        );

        assertEquals(
                new WorldPosition(
                        512.0,
                        100.0,
                        512.0
                ),
                renderer.player()
        );
    }

    @Test
    void rendersMapWithActualOreOverlay() {
        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        CapturingPngWriter writer =
                new CapturingPngWriter();

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        MapCommand command =
                new MapCommand(
                        new PrintStream(
                                buffer
                        ),
                        new FakeReader(),
                        new FakeMetadataReader(),
                        new HomeStore(
                                tempDir.resolve(
                                        "home.properties"
                                )
                        ),
                        new MarkerStore(
                                tempDir.resolve(
                                        "markers.csv"
                                )
                        ),
                        new CapturingRenderer(),
                        new UserMarkerRenderer(),
                        writer,
                        "render"
                );

        command.run(
                new String[]{
                        savePath.toString(),
                        "--radius",
                        "64",
                        "--out",
                        tempDir.resolve(
                                "map.png"
                        ).toString(),
                        "--actual-ore",
                        "nativecopper",
                        "--actual-y-min",
                        "0",
                        "--actual-y-max",
                        "16"
                }
        );

        assertNotNull(
                writer.image
        );

        String output =
                buffer.toString();

        assertTrue(
                output.contains(
                        "Actual ore overlay: nativecopper"
                )
        );

        assertTrue(
                output.contains(
                        "Actual ore Y filter: 0..16"
                )
        );

        assertTrue(
                output.contains(
                        "Actual ore matching blocks: 1"
                )
        );

        assertTrue(
                output.contains(
                        "Actual ore hit columns: 1"
                )
        );
    }

    @Test
    void rejectsActualYFilterWithoutActualOre() {
        MapCommand command =
                commandForValidation();

        CommandException exception =
                assertThrows(
                        CommandException.class,
                        () ->
                                command.run(
                                        new String[]{
                                                "world.vcdbs",
                                                "--radius",
                                                "64",
                                                "--out",
                                                "map.png",
                                                "--actual-y-min",
                                                "0"
                                        }
                                )
                );

        assertEquals(
                "--actual-y-min and --actual-y-max require --actual-ore",
                exception.getMessage()
        );
    }

    @Test
    void rejectsInvertedActualYFilter() {
        MapCommand command =
                commandForValidation();

        CommandException exception =
                assertThrows(
                        CommandException.class,
                        () ->
                                command.run(
                                        new String[]{
                                                "world.vcdbs",
                                                "--radius",
                                                "64",
                                                "--out",
                                                "map.png",
                                                "--actual-ore",
                                                "nativecopper",
                                                "--actual-y-min",
                                                "20",
                                                "--actual-y-max",
                                                "0"
                                        }
                                )
                );

        assertEquals(
                "--actual-y-min must not be greater than --actual-y-max",
                exception.getMessage()
        );
    }

    private MapCommand commandForValidation() {
        return new MapCommand(
                new PrintStream(
                        new ByteArrayOutputStream()
                ),
                new FakeReader(),
                new FakeMetadataReader(),
                new HomeStore(
                        tempDir.resolve(
                                "home.properties"
                        )
                ),
                new MarkerStore(
                        tempDir.resolve(
                                "markers.csv"
                        )
                ),
                new CapturingRenderer(),
                new UserMarkerRenderer(),
                new NoopPngWriter(),
                "render"
        );
    }

    private static class FakeReader
            extends VcdbsReader {

        FakeReader() {
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
                    512.0,
                    100.0,
                    512.0
            );
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            return List.of();
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            return List.of(
                    oreChunk()
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

    private static ParsedChunk oreChunk() {
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
                (5 * size + 0)
                        * size
                        + 0
                ] =
                1;

        return new ParsedChunk(
                new ChunkCoordinate(
                        16,
                        0,
                        16
                ),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private static class FakeMetadataReader
            extends WorldMetadataReader {

        @Override
        public WorldMetadata read(
                Path savePath,
                ProgressReporter progress
        ) {
            return new WorldMetadata(
                    1024,
                    256,
                    1024
            );
        }
    }

    private static class CapturingRenderer
            extends MapRenderer {

        private HomeState home =
                HomeState.absent();

        private WorldPosition player;

        @Override
        public RenderedMap render(
                WorldPosition center,
                WorldPosition player,
                HomeState home,
                List<MapChunk> chunks,
                List<SurfaceBlock> surfaceBlocks,
                RenderOptions options,
                ProgressReporter progress
        ) {
            this.player =
                    player;

            this.home =
                    home;

            return new RenderedMap(
                    new BufferedImage(
                            32,
                            32,
                            BufferedImage.TYPE_INT_ARGB
                    ),
                    new MapRenderReport(
                            32,
                            32,
                            chunks.size(),
                            0,
                            home instanceof HomeState.Present
                                    ? 2
                                    : 1,
                            RenderStyle.SIMPLE,
                            "MARKERS"
                    )
            );
        }

        HomeLocation homeLocation() {
            if (home instanceof HomeState.Present(HomeLocation location)) {
                return location;
            }

            throw new AssertionError(
                    "Expected HOME to be present"
            );
        }

        WorldPosition player() {
            return player;
        }
    }

    private static class NoopPngWriter
            extends PngWriter {

        @Override
        public void write(
                BufferedImage image,
                Path output
        ) {
        }
    }

    private static class CapturingPngWriter
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
}
