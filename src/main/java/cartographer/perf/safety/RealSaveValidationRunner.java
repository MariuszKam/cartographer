package cartographer.perf.safety;

import cartographer.save.SqliteSaveConnection;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Runs the narrow read-only operation between two independent save snapshots. */
public final class RealSaveValidationRunner {
    private final SaveSafetySnapshotter snapshotter;
    private final SaveSafetyGate gate;
    private final ReadOnlyOperation operation;

    public RealSaveValidationRunner() {
        this(new SaveSafetySnapshotter(), new SaveSafetyGate(),
                RealSaveValidationRunner::readOnlySmokeOperation);
    }

    RealSaveValidationRunner(
            SaveSafetySnapshotter snapshotter,
            SaveSafetyGate gate,
            ReadOnlyOperation operation
    ) {
        this.snapshotter = Objects.requireNonNull(snapshotter, "snapshotter is required");
        this.gate = Objects.requireNonNull(gate, "gate is required");
        this.operation = Objects.requireNonNull(operation, "operation is required");
    }

    public SaveSafetyResult validate(Path savePath) {
        Objects.requireNonNull(savePath, "save path is required");
        SaveSafetySnapshot before = snapshotter.capture(savePath);
        SaveSafetySnapshot after = null;
        Exception operationFailure = null;
        RuntimeException afterFailure = null;

        try {
            operation.execute(savePath);
        } catch (Exception failure) {
            operationFailure = failure;
        } finally {
            try {
                after = snapshotter.capture(savePath);
            } catch (RuntimeException failure) {
                afterFailure = failure;
                if (operationFailure != null) {
                    operationFailure.addSuppressed(failure);
                }
            }
        }

        if (afterFailure != null) {
            if (operationFailure != null) {
                throw new RealSaveValidationException(
                        "Save safety AFTER snapshot failed", operationFailure, null);
            }
            throw afterFailure;
        }
        SaveSafetyResult safetyResult = gate.compare(before, after);
        if (operationFailure != null) {
            throw new RealSaveValidationException(
                    "Read-only save smoke operation failed", operationFailure, safetyResult);
        }
        return safetyResult;
    }

    private static void readOnlySmokeOperation(Path savePath) throws SQLException {
        try (Connection connection = new SqliteSaveConnection().openReadOnly(savePath);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master")) {
            if (!resultSet.next()) {
                throw new SQLException("SQLite smoke query returned no row");
            }
            resultSet.getLong(1);
        }
    }

    @FunctionalInterface
    interface ReadOnlyOperation {
        void execute(Path savePath) throws Exception;
    }
}
