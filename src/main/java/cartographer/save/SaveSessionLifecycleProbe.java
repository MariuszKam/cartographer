package cartographer.save;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;

/** Operation-scoped observation of one SaveSession-owned source connection. */
public final class SaveSessionLifecycleProbe {
    private static final SaveSessionLifecycleProbe NO_OP =
            new SaveSessionLifecycleProbe(false);

    private final boolean recording;
    private int connectionsOpened;
    private int connectionsClosed;

    private SaveSessionLifecycleProbe(boolean recording) {
        this.recording = recording;
    }

    public static SaveSessionLifecycleProbe noOp() {
        return NO_OP;
    }

    public static SaveSessionLifecycleProbe recording() {
        return new SaveSessionLifecycleProbe(true);
    }

    public Connection observe(Path savePath, Connection connection) {
        Objects.requireNonNull(savePath, "save path is required");
        Objects.requireNonNull(connection, "connection is required");
        if (!recording) {
            return connection;
        }
        connectionsOpened = Math.addExact(connectionsOpened, 1);
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ClosingHandler(connection)
        );
    }

    public Snapshot snapshot() {
        return new Snapshot(connectionsOpened, connectionsClosed);
    }

    public record Snapshot(int connectionsOpened, int connectionsClosed) {
        public Snapshot {
            if (connectionsOpened < 0 || connectionsClosed < 0) {
                throw new IllegalArgumentException("lifecycle counts cannot be negative");
            }
        }
    }

    private final class ClosingHandler implements InvocationHandler {
        private final Connection delegate;
        private boolean closeObserved;

        private ClosingHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getCause();
            } finally {
                if (method.getName().equals("close")
                        && method.getParameterCount() == 0
                        && !closeObserved) {
                    closeObserved = true;
                    connectionsClosed = Math.addExact(connectionsClosed, 1);
                }
            }
        }
    }
}
