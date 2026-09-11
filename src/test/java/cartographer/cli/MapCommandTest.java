package cartographer.cli;

import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
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
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        renderer,
                        new NoopPngWriter(),
                        "render"
                );

        int exitCode =
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
                0,
                exitCode
        );

        assertEquals(
                new HomeLocation(
                        502.0,
                        532.0
                ),
                renderer.home()
                        .orElseThrow()
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
                Optional<String> playerSelector,
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
            return List.of();
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(
                Path savePath,
                ProgressReporter progress
        ) {
            return Map.of();
        }
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

        private Optional<HomeLocation> home =
                Optional.empty();

        private WorldPosition player;

        @Override
        public RenderedMap render(
                WorldPosition center,
                WorldPosition player,
                Optional<HomeLocation> home,
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
                            1,
                            1,
                            BufferedImage.TYPE_INT_ARGB
                    ),
                    new MapRenderReport(
                            1,
                            1,
                            chunks.size(),
                            0,
                            home.isPresent()
                                    ? 2
                                    : 1,
                            RenderStyle.SIMPLE,
                            "MARKERS"
                    )
            );
        }

        Optional<HomeLocation> home() {
            return home;
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
}