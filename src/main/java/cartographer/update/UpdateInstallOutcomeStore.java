package cartographer.update;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class UpdateInstallOutcomeStore {
    private final Path outcomeFile;

    public UpdateInstallOutcomeStore(Path outcomeFile) {
        this.outcomeFile = Objects.requireNonNull(
                outcomeFile,
                "outcomeFile is required"
        ).toAbsolutePath().normalize();
    }

    public Optional<UpdateInstallOutcome> consume() {
        if (!Files.isRegularFile(outcomeFile)) {
            return Optional.empty();
        }

        try {
            return Optional.of(read());
        } catch (Exception ignored) {
            return Optional.empty();
        } finally {
            try {
                Files.deleteIfExists(outcomeFile);
            } catch (IOException ignored) {
                // A stale result must never prevent application startup.
            }
        }
    }

    private UpdateInstallOutcome read() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                outcomeFile,
                StandardCharsets.US_ASCII
        )) {
            properties.load(reader);
        }

        UpdateInstallOutcome.Status status =
                UpdateInstallOutcome.Status.valueOf(
                        required(properties, "status")
                );
        ApplicationVersion version = ApplicationVersion.parse(
                required(properties, "version")
        );
        UpdateInstallOutcome.Reason reason =
                UpdateInstallOutcome.Reason.valueOf(
                        required(properties, "reason")
                );
        int exitCode = Integer.parseInt(
                required(properties, "exitCode")
        );
        return new UpdateInstallOutcome(
                status,
                version,
                reason,
                exitCode
        );
    }

    private String required(
            Properties properties,
            String key
    ) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing update outcome field: " + key
            );
        }
        return value.trim();
    }
}
