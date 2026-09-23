package cartographer.perf.snapshot;

import cartographer.save.SqliteSaveConnection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingSqliteSaveConnectionTest {

    @Test
    void countsOpenedAndClosedConnectionsExactlyOnce() {
        AtomicInteger delegateOpens = new AtomicInteger();
        AtomicInteger delegateCloses = new AtomicInteger();
        RecordingSqliteSaveConnection connections =
                new RecordingSqliteSaveConnection(
                        delegate(delegateOpens, delegateCloses)
                );

        Connection connection =
                connections.openReadOnly(Path.of("fixture.vcdbs"));

        assertEquals(
                new RecordingSqliteSaveConnection.Snapshot(1, 0),
                connections.snapshot()
        );

        assertDoesNotThrowClose(connection);
        assertDoesNotThrowClose(connection);

        assertEquals(1, delegateOpens.get());
        assertEquals(2, delegateCloses.get());
        assertEquals(
                new RecordingSqliteSaveConnection.Snapshot(1, 1),
                connections.snapshot()
        );
    }

    @Test
    void failedOpenDoesNotCreateFalseLifecycleEvidence() {
        SqliteSaveConnection failing = new SqliteSaveConnection() {
            @Override
            public Connection openReadOnly(Path savePath) {
                throw new IllegalStateException("open failed");
            }
        };
        RecordingSqliteSaveConnection connections =
                new RecordingSqliteSaveConnection(failing);

        assertThrows(
                IllegalStateException.class,
                () -> connections.openReadOnly(Path.of("fixture.vcdbs"))
        );

        assertEquals(
                new RecordingSqliteSaveConnection.Snapshot(0, 0),
                connections.snapshot()
        );
    }

    private static SqliteSaveConnection delegate(
            AtomicInteger opens,
            AtomicInteger closes
    ) {
        return new SqliteSaveConnection() {
            @Override
            public Connection openReadOnly(Path savePath) {
                opens.incrementAndGet();
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("close")
                                    && method.getParameterCount() == 0) {
                                closes.incrementAndGet();
                            }
                            return null;
                        }
                );
            }
        };
    }

    private static void assertDoesNotThrowClose(
            Connection connection
    ) {
        try {
            connection.close();
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }
}
