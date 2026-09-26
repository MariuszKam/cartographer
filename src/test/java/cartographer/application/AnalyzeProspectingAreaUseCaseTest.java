package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockIdentity;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapAssembler;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.BlockInfo;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.resource.ActualOreObservation;
import cartographer.prospecting.FusedProspectingObservationProvider;
import cartographer.prospecting.FusedProspectingResult;
import cartographer.prospecting.OreRockCompatibility;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.ProspectingRank;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceOverlayCell;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyzeProspectingAreaUseCaseTest {
    @Test
    void combinesActualOreAndGeologySignalEvidenceDeterministically() {
        TestReader reader = new TestReader();
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
        CountingFusedProvider provider = new CountingFusedProvider(
                Map.of("tin", ActualOreObservation.OBSERVED)
        );
        AnalyzeProspectingAreaUseCase useCase = new AnalyzeProspectingAreaUseCase(
                reader,
                resources,
                (resource, rock) -> OreRockCompatibility.COMPATIBLE,
                provider,
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
                List.of()
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

    @Test
    void multiResourceRequestUsesOneFusedProviderCallAndRetainsRockMap() {
        TestReader reader = new TestReader();
        ResourceAnalyzer resources = new ResourceAnalyzer() {
            @Override
            public List<String> matchingKeys(
                    List<ServerMapRegion> regions,
                    String query
            ) {
                return List.of(query);
            }

            @Override
            public List<ResourceOverlayCell> overlayCells(
                    List<ServerMapRegion> regions,
                    String resourceKey,
                    double minimumRelativeSignal
            ) {
                return List.of();
            }
        };
        CountingFusedProvider provider = new CountingFusedProvider();
        AnalyzeProspectingAreaUseCase useCase = new AnalyzeProspectingAreaUseCase(
                reader,
                resources,
                OreRockCompatibilityProvider.unknown(),
                provider,
                new SaveSessionFactory(
                        new TestConnectionFactory(),
                        reader,
                        new TestMetadataReader()
                )
        );

        ProspectingAreaResult result = useCase.execute(
                new ProspectingAreaRequest(
                        Path.of("world.vcdbs"),
                        Optional.of(new WorldPosition(16, 0, 16)),
                        16,
                        List.of("copper", "tin")
                )
        );

        assertEquals(1, provider.sessionCalls.get());
        assertEquals(List.of("copper", "tin"), provider.lastResources);
        assertEquals(2, result.assessments().size());
        assertEquals(
                List.of("copper", "tin"),
                result.assessments().stream()
                        .map(assessment -> assessment.candidate().resourceKey())
                        .toList()
        );
        assertEquals(provider.rockMap, result.rockMap().orElseThrow());
    }

    private static final class CountingFusedProvider
            implements FusedProspectingObservationProvider {
        private final AtomicInteger sessionCalls = new AtomicInteger();
        private final Map<String, ActualOreObservation> configuredObservations;
        private List<String> lastResources = List.of();
        private final RockMap rockMap = createRockMap();

        private CountingFusedProvider() {
            this(Map.of());
        }

        private CountingFusedProvider(
                Map<String, ActualOreObservation> configuredObservations
        ) {
            this.configuredObservations = Map.copyOf(configuredObservations);
        }

        private static RockMap createRockMap() {
            RockIdentity granite = new RockIdentity(
                    7,
                    "game:rock-granite",
                    "game",
                    "granite"
            );
            RockMapAssembler assembler = new RockMapAssembler(
                    new WorldPosition(16, 0, 16),
                    16,
                    0,
                    64,
                    RockMapMode.UPPER_ROCK,
                    RockCatalog.from(Map.of(
                            granite.blockId(),
                            new BlockInfo(
                                    granite.blockId(),
                                    granite.code()
                            )
                    ))
            );
            assembler.accept(
                    RockColumnSample.observed(
                            16,
                            16,
                            granite,
                            5
                    )
            );
            return assembler.finish();
        }

        @Override
        public FusedProspectingResult analyze(
                SaveSession session,
                WorldPosition center,
                int radius,
                List<String> resourceKeys
        ) {
            sessionCalls.incrementAndGet();
            lastResources = List.copyOf(resourceKeys);
            return result(resourceKeys);
        }

        @Override
        public FusedProspectingResult analyze(
                Path savePath,
                WorldPosition center,
                int radius,
                List<String> resourceKeys
        ) {
            throw new AssertionError("session overload must be used");
        }

        private FusedProspectingResult result(List<String> resources) {
            Map<String, ActualOreObservation> observations =
                    new java.util.LinkedHashMap<>();
            for (String resource : resources) {
                observations.put(
                        resource,
                        configuredObservations.getOrDefault(
                                resource,
                                ActualOreObservation.NOT_OBSERVED
                        )
                );
            }
            return new FusedProspectingResult(rockMap, observations);
        }
    }

    private static final class TestReader extends VcdbsReader {
        private TestReader() {
            super(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(
                Connection connection
        ) {
            return Map.of(
                    7, new BlockInfo(7, "game:rock-granite"),
                    8, new BlockInfo(8, "game:rock-shale")
            );
        }

        @Override
        public List<ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            return List.of();
        }

    }

    private static final class TestMetadataReader extends WorldMetadataReader {
        @Override
        protected WorldMetadata read(Connection connection) {
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
