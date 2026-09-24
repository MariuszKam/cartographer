package cartographer.save;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Operation-scoped owner of one immutable, read-only save connection.
 *
 * <p>A session is confined to its creating/analysis thread. It is not a
 * general-purpose thread-safe connection or a background worker. Decode
 * workers may process payloads produced by the bounded reader pipeline, but
 * must not use this session's JDBC connection.</p>
 */
public final class SaveSession implements AutoCloseable {
    private final Path savePath;
    private final Connection connection;
    private final SaveSnapshot snapshot;
    private boolean closed;

    SaveSession(Path savePath, Connection connection, SaveSnapshot snapshot) {
        this.savePath = SavePathIdentity.normalize(savePath);
        this.connection = Objects.requireNonNull(connection, "connection is required");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot is required");
    }

    public Path savePath() {
        return savePath;
    }

    /**
     * Verifies that request-scoped save data belongs to this session.
     * This is an identity check only; it performs no filesystem I/O.
     */
    public void requireSameSave(Path requestedSavePath) {
        ensureOpen();
        Path normalized = SavePathIdentity.normalize(requestedSavePath);
        if (!savePath.equals(normalized)) {
            throw new IllegalArgumentException(
                    "Save session path does not match requested save path: "
                            + savePath + " != " + normalized
            );
        }
    }

    public SaveSnapshot snapshot() {
        ensureOpen();
        return snapshot;
    }

    /**
     * Package-private seam for session-aware save readers. The returned
     * connection is borrowed; only this session may close it.
     */
    Connection connection() {
        ensureOpen();
        return connection;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new SaveException("Cannot close save session: " + savePath, exception);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("save session is closed: " + savePath);
        }
    }
}
