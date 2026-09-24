package cartographer.cli;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.RenderDataCacheReport;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.MapRenderReport;
import cartographer.render.MapRenderer;
import cartographer.render.MapTerrainPreparation;
import cartographer.render.OverlayRenderReport;
import cartographer.render.PngWriter;
import cartographer.render.RenderOptions;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderedMap;
import cartographer.render.SurfaceRenderData;
import cartographer.render.UserMarkerRenderer;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.scanner.SurfaceDiagnosticsSummary;
import cartographer.scanner.SurfaceMap;
import cartographer.save.ReadDiagnostics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

        MapCommand command = realMapCommand(
                new PrintStream(new ByteArrayOutputStream()),
                new FakeReader(),
                new FakeMetadataReader(),
                homeStore,
                markerStore,
                renderer,
                new NoopPngWriter()
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

        MapCommand command = realMapCommand(
                new PrintStream(buffer),
                new FakeReader(),
                new FakeMetadataReader(),
                new HomeStore(tempDir.resolve("home.properties")),
                new MarkerStore(tempDir.resolve("markers.csv")),
                new CapturingRenderer(),
                writer
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

    @Test
    void delegatesMapRenderingToApplicationUseCase() {
        RecordingUseCase useCase =
                new RecordingUseCase(
                        new FakeReader(),
                        new FakeMetadataReader(),
                        new HomeStore(
                                tempDir.resolve("home.properties")
                        ),
                        new MarkerStore(
                                tempDir.resolve("markers.csv")
                        ),
                        new MapRenderer(),
                        new UserMarkerRenderer(),
                        new ActualOreOverlayPainter()
                );

        MapCommand command =
                new MapCommand(
                        new PrintStream(
                                new ByteArrayOutputStream()
                        ),
                        new NoopPngWriter(),
                        useCase,
                        "render"
                );

        command.run(
                new String[]{
                        "world.vcdbs",
                        "--radius",
                        "64",
                        "--scale",
                        "2",
                        "--style",
                        "topographic",
                        "--layers",
                        "terrain,markers",
                        "--actual-ore",
                        "copper",
                        "--actual-y-min",
                        "0",
                        "--out",
                        "map.png"
                }
        );

        assertEquals(
                "copper",
                useCase.request.oreMatch().orElseThrow()
        );

        assertEquals(
                0,
                useCase.request.yFilter().minInclusive()
        );

        assertEquals(
                2,
                useCase.request.pixelsPerBlock()
        );

        assertEquals(
                RenderStyle.TOPOGRAPHIC,
                useCase.request.style()
        );
    }

    @Test
    void delegatesSoilFertilityLayerAndPrintsSurfaceDiagnostics() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RecordingUseCase useCase = new RecordingUseCase(
                new FakeReader(),
                new FakeMetadataReader(),
                new HomeStore(tempDir.resolve("home.properties")),
                new MarkerStore(tempDir.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter()
        );
        MapCommand command = new MapCommand(
                new PrintStream(buffer),
                new NoopPngWriter(),
                useCase,
                "render"
        );

        command.run(new String[]{
                "world.vcdbs",
                "--radius", "64",
                "--layers", "soil_fertility",
                "--out", "map.png"
        });

        assertEquals(Set.of(RenderLayer.SOIL_FERTILITY), useCase.request.layers());
        String output = buffer.toString();
        assertTrue(output.contains("Layers: SOIL_FERTILITY"));
        assertTrue(output.contains("Surface columns:"));
    }

    private MapCommand commandForValidation() {
        FakeReader reader = new FakeReader();
        FakeMetadataReader metadataReader = new FakeMetadataReader();
        return realMapCommand(
                new PrintStream(
                        new ByteArrayOutputStream()
                ),
                reader,
                metadataReader,
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
                new NoopPngWriter()
        );
    }

    private MapCommand realMapCommand(
            PrintStream out,
            FakeReader reader,
            FakeMetadataReader metadataReader,
            HomeStore homeStore,
            MarkerStore markerStore,
            MapRenderer renderer,
            PngWriter pngWriter
    ) {
        RenderActualOreMapUseCase useCase = new RenderActualOreMapUseCase(
                reader,
                homeStore,
                markerStore,
                renderer,
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                new cartographer.scanner.MultiActualBlockMapScanner(),
                new cartographer.application.OreChunkPositionPlanner(),
                new SaveSessionFactory(new TestConnectionFactory(), reader, metadataReader)
        );
        return new MapCommand(out, pngWriter, useCase, "render");
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
                SaveSession session,
                cartographer.application.ProgressReporter progress
        ) {
            return new WorldPosition(
                    512.0,
                    100.0,
                    512.0
            );
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                SaveSession session,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
        ) {
            return List.of();
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                SaveSession session,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
        ) {
            return List.of(
                    oreChunk()
            );
        }

        @Override
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                SaveSession session,
                java.util.Collection<cartographer.model.MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            return new MapChunkStreamStats(
                    coordinates.size(),
                    coordinates.isEmpty() ? 0 : 1,
                    0,
                    0,
                    0,
                    0
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            if (positions.isEmpty()) {
                return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
            }

            consumer.accept(oreChunk());
            return new ChunkStreamStats(
                    positions.size(),
                    1,
                    1,
                    1,
                    0,
                    0
            );
        }

        @Override
        public ChunkStreamStats forEachSurfaceChunkByPositionAdaptive(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            return forEachChunkByPositionAdaptive(
                    session, positions, diagnostics, consumer, progress
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                SaveSession session,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            if (positions.isEmpty()) {
                return new SelectiveChunkStreamStats(
                        0, 0, 0, 0, 0, 0, 0, 0
                );
            }

            boolean wanted = false;
            for (int wantedBlockId : wantedBlockIds) {
                if (wantedBlockId == 1) {
                    wanted = true;
                    break;
                }
            }

            if (!wanted) {
                return new SelectiveChunkStreamStats(
                        positions.size(), 1, 0, 0, 0, 0, 0, 0
                );
            }

            consumer.accept(oreChunk());
            return new SelectiveChunkStreamStats(
                    positions.size(), 1, 1, 1, 0, 1, 0, 1
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
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
            extends MapRenderer {

        private HomeState home =
                HomeState.absent();

        private WorldPosition player;

        @Override
        public RenderedMap render(
                WorldPosition center,
                WorldPosition player,
                HomeState home,
                MapTerrainPreparation terrain,
                SurfaceRenderData surfaceData,
                SurfaceMap exactSurface,
                Map<Integer, BlockInfo> registry,
                RenderOptions options,
                cartographer.application.ProgressReporter progress
        ) {
            this.player = player;
            this.home = home;
            return new RenderedMap(
                    new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB),
                    new MapRenderReport(
                            32, 32, terrain.mapChunkCount(), 0,
                            home instanceof HomeState.Present ? 2 : 1,
                            RenderStyle.SIMPLE, "MARKERS"),
                    MapViewportGeometry.fullImage(32, 32, 0, 0, 1, 1));
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

    private static class RecordingUseCase
            extends RenderActualOreMapUseCase {

        private RenderActualOreMapRequest request;

        RecordingUseCase(
                VcdbsReader reader,
                WorldMetadataReader metadataReader,
                HomeStore homeStore,
                MarkerStore markerStore,
                MapRenderer renderer,
                UserMarkerRenderer userMarkerRenderer,
                ActualOreOverlayPainter actualOreOverlayPainter
        ) {
            super(
                    reader,
                    homeStore,
                    markerStore,
                    renderer,
                    userMarkerRenderer,
                    actualOreOverlayPainter,
                    new cartographer.scanner.MultiActualBlockMapScanner(),
                    new cartographer.application.OreChunkPositionPlanner(),
                    new SaveSessionFactory(
                            new TestConnectionFactory(),
                            reader,
                            metadataReader
                    )
            );
        }

        @Override
        public RenderActualOreMapResult execute(
                RenderActualOreMapRequest request
        ) {
            this.request = request;

            return new RenderActualOreMapResult(
                    new BufferedImage(
                            8,
                            8,
                            BufferedImage.TYPE_INT_ARGB
                    ),
                    MapViewportGeometry.fullImage(8, 8, 0, 0, 8, 8),
                    new MapRenderReport(
                            8,
                            8,
                            0,
                            0,
                            0,
                            request.style(),
                            useCaseLayers(request)
                    ),
                    SurfaceDiagnosticsSummary.empty(),
                    OverlayRenderReport.none(),
                    OverlayRenderReport.none(),
                    Optional.empty(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    0,
                    List.of(),
                    RenderDataCacheReport.disabled("test"),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }

        private String useCaseLayers(RenderActualOreMapRequest request) {
            return request.layers().stream()
                    .map(Enum::name)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(","));
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
