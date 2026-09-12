package cartographer.cli;

import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.model.HomeLocation;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.PngWriter;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void renderRequiresOutputOption() {
        CoverageCommand command =
                command(
                        new CapturingRenderer(),
                        new NoopPngWriter(),
                        "render"
                );

        org.junit.jupiter.api.Assertions.assertThrows(
                CommandException.class,
                () ->
                        command.run(
                                new String[]{
                                        tempDir.resolve(
                                                "world.vcdbs"
                                        ).toString()
                                }
                        )
        );
    }

    @Test
    void renderConvertsDisplayHomeToAbsoluteBeforeRendering() {
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

        CoverageCommand command =
                new CoverageCommand(
                        new PrintStream(
                                new ByteArrayOutputStream()
                        ),
                        new FakeReader(),
                        new FakeMetadataReader(),
                        homeStore,
                        new RegionCoverageAnalyzer(),
                        renderer,
                        new NoopPngWriter(),
                        "render"
                );

        int exitCode =
                command.run(
                        new String[]{
                                savePath.toString(),
                                "--out",
                                tempDir.resolve(
                                        "coverage.png"
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
    }

    private CoverageCommand command(
            RegionCoverageRenderer renderer,
            PngWriter pngWriter,
            String subcommand
    ) {
        return new CoverageCommand(
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
                new RegionCoverageAnalyzer(),
                renderer,
                pngWriter,
                subcommand
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
        public List<ServerMapRegion> readMapRegions(
                Path savePath,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            diagnostics.recordParsed();

            return List.of(
                    new ServerMapRegion(
                            new MapRegionCoordinate(
                                    1,
                                    1
                            ),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()
                    )
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
            extends RegionCoverageRenderer {
        private Optional<HomeLocation> home =
                Optional.empty();

        @Override
        public BufferedImage render(
                RegionCoverageSummary summary,
                WorldPosition player,
                Optional<HomeLocation> home
        ) {
            this.home =
                    home;

            return new BufferedImage(
                    1,
                    1,
                    BufferedImage.TYPE_INT_ARGB
            );
        }

        Optional<HomeLocation> home() {
            return home;
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
