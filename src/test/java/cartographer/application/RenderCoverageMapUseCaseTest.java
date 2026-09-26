package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.coverage.RegionCoverageRenderResult;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.model.BlockInfo;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeState;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.MapViewportGeometry;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderCoverageMapUseCaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void orchestratesCoverageAndReturnsSummaryAndDiagnostics() {
        FakeReader reader = new FakeReader(List.of(region(1, 2)));
        CapturingRenderer renderer = new CapturingRenderer();
        RenderCoverageMapResult result = useCase(reader, renderer).execute(request());

        assertEquals(1, result.summary().presentCells());
        assertEquals(1, result.mapRegionDiagnostics().parsed());
        assertEquals(new WorldPosition(100, 70, 200), renderer.player);
        assertTrue(result.image().getWidth() > 0);
        assertEquals(
                new MapViewportGeometry(1, 1, 0, 0, 1, 1, 0, 0, 1, 1),
                result.geometry().orElseThrow()
        );
    }

    @Test
    void convertsDisplayHomeToAbsoluteBeforeRendering() {
        FakeReader reader = new FakeReader(List.of(region(0, 0)));
        CapturingRenderer renderer = new CapturingRenderer();
        Path save = temporaryDirectory.resolve("world.vcdbs");
        HomeStore homeStore = new HomeStore(temporaryDirectory.resolve("home.properties"));
        homeStore.save(save, new DisplayPosition(-10, 0.0, 20));

        useCase(reader, renderer, homeStore).execute(new RenderCoverageMapRequest(save));

        assertEquals(HomeState.present(new WorldPosition(502, 0.0, 532)), renderer.home);
    }

    @Test
    void emptyMapregionInputProducesEmptySummary() {
        RenderCoverageMapResult result = useCase(
                new FakeReader(List.of()), new CapturingRenderer()
        ).execute(request());

        assertTrue(result.summary().empty());
        assertEquals(0, result.summary().presentCells());
        assertTrue(result.geometry().isEmpty());
    }

    private RenderCoverageMapRequest request() {
        return new RenderCoverageMapRequest(temporaryDirectory.resolve("world.vcdbs"));
    }

    private RenderCoverageMapUseCase useCase(FakeReader reader, CapturingRenderer renderer) {
        return useCase(reader, renderer,
                new HomeStore(temporaryDirectory.resolve("home.properties")));
    }

    private RenderCoverageMapUseCase useCase(
            FakeReader reader,
            CapturingRenderer renderer,
            HomeStore homeStore
    ) {
        return new RenderCoverageMapUseCase(
                reader,
                sessionFactory(
                        reader
                ),
                homeStore,
                new RegionCoverageAnalyzer(),
                renderer
        );
    }

    private ServerMapRegion region(int x, int z) {
        return new ServerMapRegion(new MapRegionCoordinate(x, z),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Map.of(), List.of());
    }

    private static final class FakeReader extends VcdbsReader {
        private final List<ServerMapRegion> regions;

        private FakeReader(List<ServerMapRegion> regions) {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
            this.regions = regions;
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
                ProgressReporter progress
        ) {
            diagnostics.recordParsed();
            return regions;
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            return new WorldPosition(100, 70, 200);
        }
    }

    private SaveSessionFactory sessionFactory(
            FakeReader reader
    ) {
        return new SaveSessionFactory(
                new TestConnectionFactory(),
                reader,
                new FakeMetadataReader()
        );
    }

    private static final class FakeMetadataReader
            extends WorldMetadataReader {

        @Override
        protected WorldMetadata read(Connection connection) {
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

    private static final class CapturingRenderer extends RegionCoverageRenderer {
        private WorldPosition player;
        private HomeState home;

        @Override
        public RegionCoverageRenderResult render(
                RegionCoverageSummary summary, WorldPosition player, HomeState home
        ) {
            this.player = player;
            this.home = home;
            return new RegionCoverageRenderResult(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    summary.empty()
                            ? Optional.empty()
                            : Optional.of(new MapViewportGeometry(
                            1, 1, 0, 0, 1, 1, 0, 0, 1, 1
                    ))
            );
        }
    }
}
