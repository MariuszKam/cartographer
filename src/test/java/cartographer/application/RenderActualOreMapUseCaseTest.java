package cartographer.application;

import cartographer.cli.ProgressReporter;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderActualOreMapUseCaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesSelectiveReaderForOreAndStreamsMatchingChunk() {
        FakeReader reader = new FakeReader(
                Map.of(1, new BlockInfo(1, "ore-cassiterite-granite"))
        );
        RenderActualOreMapResult result = execute(reader);

        assertEquals(1, reader.selectiveCalls);
        assertEquals(0, reader.legacyChunkCalls);
        assertEquals(1, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(
                ActualBlockMatchMode.ORE_CODE,
                result.actualOreOverlays().getFirst().spec().matchMode()
        );
    }

    @Test
    void avoidsSelectiveReaderWhenRegistryHasNoMatchingIds() {
        FakeReader reader = new FakeReader(Map.of());
        RenderActualOreMapResult result = execute(reader);

        assertEquals(0, reader.selectiveCalls);
        assertEquals(0, result.actualOreOverlays().getFirst().map().matchingBlocks());
        assertTrue(result.actualOreOverlays().getFirst().map().cells().isEmpty());
    }

    private RenderActualOreMapResult execute(FakeReader reader) {
        WorldMetadataReader metadataReader = new WorldMetadataReader() {
            @Override
            public WorldMetadata read(Path savePath) {
                return new WorldMetadata(128, 256, 128);
            }
        };
        RenderActualOreMapUseCase useCase = new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                new HomeStore(temporaryDirectory.resolve("home.properties")),
                new MarkerStore(temporaryDirectory.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter()
        );
        ActualOreOverlaySpec spec = new ActualOreOverlaySpec(
                "Cassiterite",
                "cassiterite",
                Color.ORANGE,
                ActualBlockMatchMode.ORE_CODE
        );
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                List.of(spec)
        );
        return useCase.execute(request);
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private int selectiveCalls;
        private int legacyChunkCalls;

        private FakeReader(Map<Integer, BlockInfo> registry) {
            super(null, null, null, null);
            this.registry = registry;
        }

        @Override
        public WorldPosition readPlayerPosition(Path savePath) {
            return new WorldPosition(64, 64, 64);
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                Path savePath,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics
        ) {
            return List.of();
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radius,
                ReadDiagnostics diagnostics
        ) {
            legacyChunkCalls++;
            throw new AssertionError("legacy chunk lookup must not be used");
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return registry;
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIds(
                Path savePath,
                java.util.Collection<cartographer.model.ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                java.util.function.Consumer<ParsedChunk> consumer
        ) {
            selectiveCalls++;
            if (registry.containsKey(1)) {
                int[] blocks = new int[]{1};
                consumer.accept(new ParsedChunk(
                        new ChunkCoordinate(2, 0, 2),
                        5,
                        1,
                        1,
                        1,
                        blocks
                ));
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    1,
                    registry.containsKey(1) ? 1 : 0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    registry.containsKey(1) ? 1 : 0,
                    0,
                    1
            );
        }
    }
}
