package cartographer.perf.jfr;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record JfrRecordingPlan(
        Path destination,
        long maxSizeBytes,
        String recordingName,
        JfrConfiguration configuration
) {
    public JfrRecordingPlan {
        Objects.requireNonNull(destination, "destination is required");
        if (!destination.toString().toLowerCase(Locale.ROOT).endsWith(".jfr")) {
            throw new IllegalArgumentException("destination must have a .jfr extension");
        }
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive");
        }
        recordingName = required(recordingName, "recordingName");
        Objects.requireNonNull(configuration, "configuration is required");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
