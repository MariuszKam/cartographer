package cartographer.save;

import cartographer.application.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaveSessionLifecycleProbeTest {
    @Test
    void recordsOneOpenAndOneCloseWhenSessionCloseIsRepeated() {
        SaveSessionLifecycleProbe probe = SaveSessionLifecycleProbe.recording();
        SaveSession session = factory(probe, false).open(Path.of("fixture.vcdbs"));

        session.close();
        session.close();

        assertEquals(new SaveSessionLifecycleProbe.Snapshot(1, 1), probe.snapshot());
    }

    @Test
    void failedSnapshotInitializationStillClosesTheObservedConnection() {
        SaveSessionLifecycleProbe probe = SaveSessionLifecycleProbe.recording();

        assertThrows(IllegalStateException.class,
                () -> factory(probe, true).open(Path.of("fixture.vcdbs")));

        assertEquals(new SaveSessionLifecycleProbe.Snapshot(1, 1), probe.snapshot());
    }

    @Test
    void sessionIdentityAndClosedStateAreEnforced() {
        SaveSession session = factory(SaveSessionLifecycleProbe.recording(), false)
                .open(Path.of("fixture.vcdbs"));
        assertThrows(IllegalArgumentException.class,
                () -> session.requireSameSave(Path.of("other.vcdbs")));
        session.close();
        assertThrows(IllegalStateException.class, session::snapshot);
        assertThrows(IllegalStateException.class,
                () -> session.requireSameSave(Path.of("fixture.vcdbs")));
    }

    private static SaveSessionFactory factory(
            SaveSessionLifecycleProbe probe,
            boolean failMetadata
    ) {
        SqliteSaveConnection connections = new SqliteSaveConnection() {
            @Override
            public Connection openReadOnly(Path savePath) {
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> null
                );
            }
        };
        VcdbsReader reader = new VcdbsReader(null, null, null, null) {
            @Override
            protected Map<Integer, BlockInfo> readBlockRegistry(Connection connection) {
                return Map.of();
            }
        };
        WorldMetadataReader metadata = new WorldMetadataReader() {
            @Override
            protected WorldMetadata read(Connection connection, ProgressReporter progress) {
                if (failMetadata) {
                    throw new IllegalStateException("fixture initialization failure");
                }
                return new WorldMetadata(32, 256, 32);
            }
        };
        return new SaveSessionFactory(connections, reader, metadata, probe);
    }
}
