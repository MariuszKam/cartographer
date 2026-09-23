package cartographer.perf.snapshot;

import cartographer.save.SqliteSaveConnection;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Validation-only source connection counter for PF-2.8/PF-3 warm-path evidence.
 *
 * <p>The production {@code SaveSessionFactory} remains unaware of validation
 * instrumentation. This connection factory wraps the normal read-only source
 * connection and records open/close lifecycle events for the validation
 * runners only.</p>
 */
final class RecordingSqliteSaveConnection extends SqliteSaveConnection {
    private final SqliteSaveConnection delegate;
    private final AtomicInteger connectionsOpened = new AtomicInteger();
    private final AtomicInteger connectionsClosed = new AtomicInteger();

    RecordingSqliteSaveConnection() {
        this(new SqliteSaveConnection());
    }

    RecordingSqliteSaveConnection(
            SqliteSaveConnection delegate
    ) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate is required"
        );
    }

    @Override
    public Connection openReadOnly(
            Path savePath
    ) {
        Connection connection =
                delegate.openReadOnly(savePath);

        connectionsOpened.incrementAndGet();

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ClosingHandler(connection)
        );
    }

    Snapshot snapshot() {
        return new Snapshot(
                connectionsOpened.get(),
                connectionsClosed.get()
        );
    }

    record Snapshot(
            int connectionsOpened,
            int connectionsClosed
    ) {
        Snapshot {
            if (connectionsOpened < 0 || connectionsClosed < 0) {
                throw new IllegalArgumentException(
                        "lifecycle counts cannot be negative"
                );
            }
        }
    }

    private final class ClosingHandler implements InvocationHandler {
        private final Connection delegateConnection;
        private final AtomicBoolean closeObserved = new AtomicBoolean();

        private ClosingHandler(
                Connection delegateConnection
        ) {
            this.delegateConnection = Objects.requireNonNull(
                    delegateConnection,
                    "connection is required"
            );
        }

        @Override
        public Object invoke(
                Object proxy,
                java.lang.reflect.Method method,
                Object[] args
        ) throws Throwable {
            try {
                return method.invoke(
                        delegateConnection,
                        args
                );
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            } finally {
                if (method.getName().equals("close")
                        && method.getParameterCount() == 0
                        && closeObserved.compareAndSet(false, true)) {
                    connectionsClosed.incrementAndGet();
                }
            }
        }
    }
}
