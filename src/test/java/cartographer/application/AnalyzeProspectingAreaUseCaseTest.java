package cartographer.application;

import cartographer.geology.rock.RockIdentity;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.prospecting.ActualOreObservationProvider;
import cartographer.prospecting.OreRockCompatibility;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.ProspectingRank;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceOverlayCell;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyzeProspectingAreaUseCaseTest {
    @Test
    void combinesActualOreAndGeologySignalEvidenceDeterministically() {
        TestReader reader = new TestReader();
        RenderRockMapUseCase rockUseCase = new RenderRockMapUseCase(
                reader,
                new TestMetadataReader(),
                new RockMapRenderer()
        );
        ResourceAnalyzer resources = new ResourceAnalyzer() {
            @Override
            public List<String> resourceKeys(List<ServerMapRegion> regions) {
                return List.of("copper", "tin");
            }

            @Override
            public List<ResourceOverlayCell> overlayCells(
                    List<ServerMapRegion> regions,
                    String resourceKey,
                    double minimumRelativeSignal
            ) {
                return List.of(new ResourceOverlayCell(
                        resourceKey,
                        new MapRegionCoordinate(0, 0),
                        0,
                        0,
                        1,
                        0.8,
                        0,
                        0,
                        32,
                        32
                ));
            }
        };
        AnalyzeProspectingAreaUseCase useCase = new AnalyzeProspectingAreaUseCase(
                reader,
                rockUseCase,
                resources,
                (resource, rock) -> OreRockCompatibility.COMPATIBLE,
                (resource, save, center, radius) -> resource.equals("tin"),
                new SaveSessionFactory(
                        new TestConnectionFactory(),
                        reader,
                        new TestMetadataReader()
                )
        );

        var result = useCase.execute(new ProspectingAreaRequest(
                Path.of("world.vcdbs"),
                Optional.of(new WorldPosition(16, 0, 16)),
                16,
                Optional.empty()
        ));

        assertEquals(List.of("tin", "copper"), result.assessments().stream()
                .map(assessment -> assessment.candidate().resourceKey())
                .toList());
        assertEquals(ProspectingRank.CONFIRMED, result.assessments().getFirst().rank());
        assertEquals(ProspectingRank.STRONG, result.assessments().get(1).rank());
        assertEquals(List.of("game:rock-granite"), result.assessments().getFirst()
                .candidate().evidence().observedHostRocks().stream()
                .map(RockIdentity::code)
                .toList());
    }

    private static final class TestReader extends VcdbsReader {
        private TestReader() {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        }

        @Override
        public Map<Integer, BlockInfo> readBlockRegistry(Path savePath) {
            return Map.of(
                    7, new BlockInfo(7, "game:rock-granite"),
                    8, new BlockInfo(8, "game:rock-shale")
            );
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            return readBlockRegistry(Path.of("world.vcdbs"));
        }

        @Override
        public List<ServerMapRegion> readMapRegions(
                Path savePath,
                ReadDiagnostics diagnostics
        ) {
            return List.of();
        }

        @Override
        public List<ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            return List.of();
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer
        ) {
            return coverage(positions, consumer);
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                SaveSession session,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            return coverage(positions, consumer);
        }

        private SelectiveChunkStreamStats coverage(
                java.util.Collection<ChunkPosition> positions,
                Consumer<SelectiveChunkVisit> consumer
        ) {
            int size = ChunkCoordinate.SIZE_BLOCKS;
            int[] blocks = new int[size * size * size];
            java.util.Arrays.fill(blocks, 7);
            for (ChunkPosition position : positions) {
                consumer.accept(SelectiveChunkVisit.decoded(
                        position,
                        new ParsedChunk(
                                new ChunkCoordinate(position.x(), position.y(), position.z()),
                                0,
                                size,
                                size,
                                size,
                                blocks
                        )
                ));
            }
            return new SelectiveChunkStreamStats(
                    positions.size(),
                    1,
                    positions.size(),
                    positions.size(),
                    0,
                    positions.size(),
                    0,
                    1
            );
        }

        @Override
        public SelectiveChunkStreamStats forEachChunkByPositionMatchingBlockIdsWithCoverage(
                Path savePath,
                java.util.Collection<ChunkPosition> positions,
                int[] wantedBlockIds,
                ReadDiagnostics diagnostics,
                Consumer<SelectiveChunkVisit> consumer,
                ProgressReporter progress
        ) {
            return forEachChunkByPositionMatchingBlockIdsWithCoverage(
                    savePath, positions, wantedBlockIds, diagnostics, consumer
            );
        }
    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(Connection connection, ProgressReporter progress) {
            return new WorldMetadata(64, 64, 64);
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
}
