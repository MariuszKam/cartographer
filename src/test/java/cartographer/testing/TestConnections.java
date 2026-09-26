package cartographer.testing;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Objects;

/** Small JDBC connection proxies for tests that only need lifecycle ownership. */
public final class TestConnections {
    private TestConnections() {
    }

    public static Connection noOp() {
        return onClose(() -> { });
    }

    public static Connection onClose(Runnable closeAction) {
        Objects.requireNonNull(closeAction, "closeAction is required");
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "TestConnection@"
                                    + Integer.toHexString(System.identityHashCode(proxy));
                            default -> throw new AssertionError(
                                    "Unexpected Object method: " + method.getName()
                            );
                        };
                    }
                    if (method.getName().equals("close")
                            && method.getParameterCount() == 0) {
                        closeAction.run();
                    }
                    return null;
                }
        );
    }
}
