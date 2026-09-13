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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        assertArrayEquals(new int[]{1}, reader.lastWantedBlockIds);
        assertTrue(!reader.lastPositions.isEmpty());
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

    @Test
    void twoOverlaysPreserveOrderAndUseOneSelectiveLookup() {
        FakeReader reader = new FakeReader(Map.of(
                1, new BlockInfo(1, "ore-cassiterite-granite"),
                2, new BlockInfo(2, "ore-nativecopper-granite")
        ));
        RenderActualOreMapResult result = execute(
                reader,
                List.of(
                        new ActualOreOverlaySpec("Cassiterite", "cassiterite", Color.ORANGE, ActualBlockMatchMode.ORE_CODE),
                        new ActualOreOverlaySpec("Copper", "nativecopper", Color.RED, ActualBlockMatchMode.ORE_CODE)
                )
        );

        assertEquals(1, reader.selectiveCalls);
        assertEquals("Cassiterite", result.actualOreOverlays().get(0).spec().displayName());
        assertEquals("Copper", result.actualOreOverlays().get(1).spec().displayName());
    }

    @Test
    void genericSubstringMatchesNonOreCodeButOreCodeDoesNot() {
        FakeReader genericReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult generic = execute(
                genericReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.GENERIC_SUBSTRING
                ))
        );
        FakeReader oreReader = new FakeReader(
                Map.of(3, new BlockInfo(3, "rock-mysteryium-granite")),
                3
        );
        RenderActualOreMapResult ore = execute(
                oreReader,
                List.of(new ActualOreOverlaySpec(
                        "Mysteryium",
                        "mysteryium",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );

        assertEquals(1, generic.actualOreOverlays().getFirst().map().matchingBlocks());
        assertEquals(0, oreReader.selectiveCalls);
        assertEquals(0, ore.actualOreOverlays().getFirst().map().matchingBlocks());
    }

    private RenderActualOreMapResult execute(FakeReader reader) {
        return execute(
                reader,
                List.of(new ActualOreOverlaySpec(
                        "Cassiterite",
                        "cassiterite",
                        Color.ORANGE,
                        ActualBlockMatchMode.ORE_CODE
                ))
        );
    }

    private RenderActualOreMapResult execute(
            FakeReader reader,
            List<ActualOreOverlaySpec> specs
    ) {
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
        RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                temporaryDirectory.resolve("save.vcdbs"),
                16,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN),
                Optional.of("cassiterite"),
                ActualBlockYFilter.unbounded(),
                Optional.of(new WorldPosition(64, 64, 64)),
                specs
        );
        return useCase.execute(request);
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private int selectiveCalls;
        private int legacyChunkCalls;
        private final int fakeBlockId;
        private int[] lastWantedBlockIds = new int[0];
        private List<cartographer.model.ChunkPosition> lastPositions = List.of();

        private FakeReader(Map<Integer, BlockInfo> registry) {
            this(registry, 1);
        }

        private FakeReader(Map<Integer, BlockInfo> registry, int fakeBlockId) {
            super(null, null, null, null);
            this.registry = registry;
            this.fakeBlockId = fakeBlockId;
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
            lastWantedBlockIds = wantedBlockIds.clone();
            lastPositions = List.copyOf(positions);
            if (contains(wantedBlockIds, fakeBlockId)) {
                int[] blocks = new int[]{fakeBlockId};
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

        private boolean contains(int[] values, int wanted) {
            for (int value : values) {
                if (value == wanted) {
                    return true;
                }
            }
            return false;
        }
    }
}
