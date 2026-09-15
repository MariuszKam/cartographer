package cartographer.cli;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
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
import cartographer.render.MapTerrainPreparation;
import cartographer.render.OverlayRenderReport;
import cartographer.render.PngWriter;
import cartographer.render.RenderOptions;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.RenderedMap;
import cartographer.render.UserMarkerRenderer;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.SurfaceScanResult;
import cartographer.save.ReadDiagnostics;
import cartographer.save.ChunkStreamStats;
import cartographer.save.MapChunkStreamStats;
import cartographer.save.SelectiveChunkStreamStats;
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
                        new ActualBlockMapScanner(),
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
                new ActualBlockMapScanner(),
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
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics,
                cartographer.application.ProgressReporter progress
        ) {
            return List.of();
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
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
                Path savePath,
                java.util.Collection<cartographer.model.MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer
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
        public MapChunkStreamStats forEachMapChunkByCoordinate(
                Path savePath,
                java.util.Collection<cartographer.model.MapChunkCoordinate> coordinates,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<MapChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            return forEachMapChunkByCoordinate(
                    savePath, coordinates, diagnostics, consumer
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            return forEachChunkByPosition(
                    savePath, positions, diagnostics, consumer
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPositionAdaptive(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            return forEachChunkByPositionAdaptive(
                    savePath, positions, diagnostics, consumer
            );
        }

        @Override
        public ChunkStreamStats forEachChunkByPosition(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            if (positions.isEmpty()) {
                return new ChunkStreamStats(0, 0, 0, 0, 0, 0);
            }

            ParsedChunk chunk = oreChunk();
            consumer.accept(chunk);
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
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                Path savePath,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            return forEachChunkByPositionMatchingBlockIds(
                    savePath, positions, wantedBlockIds, diagnostics, consumer
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsAdaptive(
                Path savePath,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer,
                cartographer.application.ProgressReporter progress
        ) {
            return forEachChunkByPositionMatchingBlockIdsAdaptive(
                    savePath, positions, wantedBlockIds, diagnostics, consumer
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
                Path savePath,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            if (positions.isEmpty()) {
                return new SelectiveChunkStreamStats(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
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
                        positions.size(),
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                );
            }

            consumer.accept(oreChunk());
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    1,
                    1,
                    1,
                    0,
                    1,
                    0,
                    1
            );
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(
                Path savePath,
                cartographer.application.ProgressReporter progress
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
                cartographer.application.ProgressReporter progress
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

        @Override
        public RenderedMap render(
                WorldPosition center,
                WorldPosition player,
                HomeState home,
                MapTerrainPreparation terrain,
                List<SurfaceBlock> surfaceBlocks,
                RenderOptions options,
                cartographer.application.ProgressReporter progress
        ) {
            this.player = player;
            this.home = home;
            return new RenderedMap(
                    new BufferedImage(
                            32,
                            32,
                            BufferedImage.TYPE_INT_ARGB
                    ),
                    new MapRenderReport(
                            32,
                            32,
                            terrain.mapChunkCount(),
                            0,
                            home instanceof HomeState.Present ? 2 : 1,
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
                ActualBlockMapScanner actualBlockMapScanner,
                ActualOreOverlayPainter actualOreOverlayPainter
        ) {
            super(
                    reader,
                    metadataReader,
                    homeStore,
                    markerStore,
                    renderer,
                    userMarkerRenderer,
                    actualBlockMapScanner,
                    actualOreOverlayPainter
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
                    new MapRenderReport(
                            8,
                            8,
                            0,
                            0,
                            0,
                            request.style(),
                            useCaseLayers(request)
                    ),
                    new SurfaceScanResult(
                            List.of(),
                            0,
                            0,
                            0,
                            0
                    ),
                    OverlayRenderReport.none(),
                    OverlayRenderReport.none(),
                    Optional.empty(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    new ReadDiagnostics(),
                    0
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
