package cartographer.cli;

import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.coverage.RegionCoverageRenderResult;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.model.BlockInfo;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.PngWriter;
import cartographer.render.MapViewportGeometry;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
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
                        new NoopPngWriter()
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

        FakeReader reader =
                new FakeReader();

        CoverageCommand command =
                new CoverageCommand(
                        new PrintStream(
                                new ByteArrayOutputStream()
                        ),
                        reader,
                        sessionFactory(
                                reader
                        ),
                        homeStore,
                        new RegionCoverageAnalyzer(),
                        renderer,
                        new NoopPngWriter(),
                        "render"
                );

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
                new HomeLocation(
                        502.0,
                        532.0
                ),
                renderer.homeLocation()
        );
    }

    private CoverageCommand command(
            RegionCoverageRenderer renderer,
            PngWriter pngWriter
    ) {
        FakeReader reader =
                new FakeReader();

        return new CoverageCommand(
                new PrintStream(
                        new ByteArrayOutputStream()
                ),
                reader,
                sessionFactory(
                        reader
                ),
                new HomeStore(
                        tempDir.resolve(
                                "home.properties"
                        )
                ),
                new RegionCoverageAnalyzer(),
                renderer,
                pngWriter,
                "render"
        );
    }

    private SaveSessionFactory sessionFactory(
            FakeReader reader
    ) {
        SqliteSaveConnection connections =
                new SqliteSaveConnection() {
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
                };

        return new SaveSessionFactory(
                connections,
                reader,
                new FakeMetadataReader()
        );
    }

    private static class FakeReader
            extends VcdbsReader {

        FakeReader() {
            super(
                    null,
                    null,
                    null,
                    null
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of();
        }

        @Override
        public List<ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
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
                            Optional.empty(),
                            Map.of(),
                            List.of()
                    )
            );
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                cartographer.application.ProgressReporter progress
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

    private static class CapturingRenderer
            extends RegionCoverageRenderer {

        private HomeState home =
                HomeState.absent();

        @Override
        public RegionCoverageRenderResult render(
                RegionCoverageSummary summary,
                WorldPosition player,
                HomeState home
        ) {
            this.home =
                    home;

            return new RegionCoverageRenderResult(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    Optional.of(MapViewportGeometry.fullImage(1, 1, 0, 0, 1, 1))
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
