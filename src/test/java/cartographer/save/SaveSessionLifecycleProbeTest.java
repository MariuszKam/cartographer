package cartographer.save;

import cartographer.testing.TestConnections;
import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaveSessionLifecycleProbeTest {
    @Test
    void closesOwnedConnectionOnceWhenSessionCloseIsRepeated() {
        ConnectionCounters counters = new ConnectionCounters();
        SaveSession session = factory(counters, false)
                .open(Path.of("fixture.vcdbs"));

        session.close();
        session.close();

        assertEquals(1, counters.opened.get());
        assertEquals(1, counters.closed.get());
    }

    @Test
    void failedSnapshotInitializationStillClosesOwnedConnection() {
        ConnectionCounters counters = new ConnectionCounters();

        assertThrows(IllegalStateException.class, () ->
                factory(counters, true)
                        .open(Path.of("fixture.vcdbs"))
                        .close()
        );

        assertEquals(1, counters.opened.get());
        assertEquals(1, counters.closed.get());
    }

    @Test
    void sessionIdentityAndClosedStateAreEnforced() {
        ConnectionCounters counters = new ConnectionCounters();
        SaveSession session = factory(counters, false)
                .open(Path.of("fixture.vcdbs"));

        assertThrows(
                IllegalArgumentException.class,
                () -> session.requireSameSave(Path.of("other.vcdbs"))
        );
        session.close();
        assertThrows(IllegalStateException.class, session::snapshot);
        assertThrows(
                IllegalStateException.class,
                () -> session.requireSameSave(Path.of("fixture.vcdbs"))
        );
    }

    private static SaveSessionFactory factory(
            ConnectionCounters counters,
            boolean failMetadata
    ) {
        SqliteSaveConnection connections = new SqliteSaveConnection() {
            @Override
            public Connection openReadOnly(Path savePath) {
                counters.opened.incrementAndGet();
                return TestConnections.onClose(counters.closed::incrementAndGet);
            }
        };
        VcdbsReader reader = new VcdbsReader(
                new cartographer.parser.PlayerDataParser(),
                new cartographer.parser.MapChunkParser(),
                new cartographer.parser.ChunkParser(),
                new cartographer.parser.RegistryParser()
        ) {
            @Override
            protected Map<Integer, BlockInfo> readBlockRegistry(
                    Connection connection
            ) {
                return Map.of();
            }
        };
        WorldMetadataReader metadata = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection) {
                if (failMetadata) {
                    throw new IllegalStateException(
                            "fixture initialization failure"
                    );
                }
                return new WorldMetadata(32, 256, 32);
            }
        };
        return new SaveSessionFactory(
                connections,
                reader,
                metadata
        );
    }

    private static final class ConnectionCounters {
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
    }
}
