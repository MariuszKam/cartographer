package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.MapChunk;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanner;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderSurfaceResourceMapUseCaseTest {

    @Test
    void analyzesVisibleSurfaceBlocksAndGroupsDeposits() {
        FakeReader reader = new FakeReader(
                List.of(surfaceChunk()),
                Map.of(
                        0, new BlockInfo(0, "air"),
                        1, new BlockInfo(1, "game:fire-clay-blue")
                )
        );

        RenderSurfaceResourceMapUseCase useCase = new RenderSurfaceResourceMapUseCase(
                reader,
                new WorldMetadataReader(),
                new HomeStore(Path.of("build", "surface-test-home.properties")),
                new MarkerStore(Path.of("build", "surface-test-markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new SurfaceScanner(),
                new SurfaceResourceAnalyzer(),
                new SurfaceResourceOverlayRenderer()
        );

        RenderSurfaceResourceMapResult result = useCase.execute(
                new RenderSurfaceResourceMapRequest(
                        Path.of("save.vcdbs"),
                        1,
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
                        new SurfaceResourceMatch("Fire Clay", List.of("fire", "clay")),
                        Optional.empty()
                )
        );

        assertEquals(2, result.analysis().matchingBlockCount());
        assertEquals(1, result.analysis().depositCount());
        assertEquals(2, result.analysis().deposits().getFirst().blockCount());
    }

    private ParsedChunk surfaceChunk() {
        int size = ChunkCoordinate.SIZE_BLOCKS;
        int[] blocks = new int[size * size * size];
        Arrays.fill(blocks, 0);
        blocks[(5 * size + 0) * size] = 1;
        blocks[(5 * size + 0) * size + 1] = 1;
        return new ParsedChunk(
                new ChunkCoordinate(0, 0, 0),
                0,
                size,
                size,
                size,
                blocks
        );
    }

    private static final class FakeReader extends VcdbsReader {
        private final List<ParsedChunk> chunks;
        private final Map<Integer, BlockInfo> registry;

        private FakeReader(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry) {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
            this.chunks = chunks;
            this.registry = registry;
        }

        @Override
        public WorldPosition readPlayerPosition(Path savePath) {
            return new WorldPosition(0.0, 100.0, 0.0);
        }

        @Override
        public List<MapChunk> readMapChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics
        ) {
            return List.of();
        }

        @Override
        public List<ParsedChunk> readChunksAround(
                Path savePath,
                WorldPosition center,
                int radiusBlocks,
                ReadDiagnostics diagnostics
        ) {
            return chunks;
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return registry;
        }
    }
}
