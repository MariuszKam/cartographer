package cartographer.application;

import cartographer.progress.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.resource.ResourceAnalyzer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LoadWorldOverviewUseCaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOverviewThroughOneSessionAndClosesIt() {
        Path savePath = temporaryDirectory.resolve("world.vcdbs");
        WorldMetadata metadata = new WorldMetadata(1024, 256, 2048);
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:ore-copper-native")
        );
        FakeReader reader = new FakeReader(registry, false);
        TrackingConnectionFactory connections = new TrackingConnectionFactory();
        WorldMetadataReader metadataReader = metadataReader(metadata);
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                connections,
                reader,
                metadataReader
        );
        LoadWorldOverviewUseCase useCase = new LoadWorldOverviewUseCase(
                reader,
                new ResourceAnalyzer(),
                sessionFactory
        );

        WorldOverview overview = useCase.execute(savePath);

        assertEquals(metadata, overview.metadata());
        assertEquals(registry, overview.blockRegistry());
        assertEquals(Optional.of(new WorldPosition(600, 70, 900)), overview.playerAbsolute());
        assertEquals(List.of("game:ore-copper"), overview.resourceKeys());
        assertEquals(1, connections.openCalls);
        assertTrue(connections.closed.get());
        assertEquals(1, reader.connectionRegistryCalls);
        assertEquals(1, reader.sessionMapRegionCalls);
        assertEquals(1, reader.sessionPlayerCalls);
    }

    @Test
    void playerFailureDoesNotDiscardWorldOverview() {
        Path savePath = temporaryDirectory.resolve("world-player-unavailable.vcdbs");
        WorldMetadata metadata = new WorldMetadata(1024, 256, 1024);
        Map<Integer, BlockInfo> registry = Map.of(
                2, new BlockInfo(2, "game:ore-tin-cassiterite")
        );
        FakeReader reader = new FakeReader(registry, true);
        TrackingConnectionFactory connections = new TrackingConnectionFactory();
        LoadWorldOverviewUseCase useCase = new LoadWorldOverviewUseCase(
                reader,
                new ResourceAnalyzer(),
                new SaveSessionFactory(
                        connections,
                        reader,
                        metadataReader(metadata)
                )
        );

        WorldOverview overview = useCase.execute(savePath);

        assertTrue(overview.playerAbsolute().isEmpty());
        assertEquals(metadata, overview.metadata());
        assertEquals(registry, overview.blockRegistry());
        assertEquals(List.of("game:ore-copper"), overview.resourceKeys());
        assertEquals(1, reader.sessionPlayerCalls);
        assertTrue(connections.closed.get());
    }

    private WorldMetadataReader metadataReader(WorldMetadata metadata) {
        return new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection) {
                return metadata;
            }
        };
    }

    private static final class FakeReader extends VcdbsReader {
        private final Map<Integer, BlockInfo> registry;
        private final boolean failPlayer;
        private int connectionRegistryCalls;
        private int sessionMapRegionCalls;
        private int sessionPlayerCalls;

        private FakeReader(
                Map<Integer, BlockInfo> registry,
                boolean failPlayer
        ) {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
            this.registry = registry;
            this.failPlayer = failPlayer;
        }

        @Override
        protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
            connectionRegistryCalls++;
            return registry;
        }

        @Override
        public List<ServerMapRegion> readMapRegions(
                SaveSession session,
                ReadDiagnostics diagnostics,
                ProgressReporter progress
        ) {
            sessionMapRegionCalls++;
            IntDataMap2D oreMap = new IntDataMap2D(1, 0, 0, new int[]{1});
            return List.of(new ServerMapRegion(
                    new MapRegionCoordinate(0, 0),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Map.of("game:ore-copper", oreMap),
                    List.of()
            ));
        }

        @Override
        public WorldPosition readPlayerPosition(
                SaveSession session,
                ProgressReporter progress
        ) {
            sessionPlayerCalls++;
            if (failPlayer) {
                throw new IllegalStateException("player unavailable");
            }
            return new WorldPosition(600, 70, 900);
        }

    }

    private static final class TrackingConnectionFactory extends SqliteSaveConnection {
        private int openCalls;
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public Connection openReadOnly(Path savePath) {
            openCalls++;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            closed.set(true);
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return closed.get();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            if (type == char.class) return '\0';
            return null;
        }
    }
}
