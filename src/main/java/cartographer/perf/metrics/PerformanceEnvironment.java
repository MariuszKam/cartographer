package cartographer.perf.metrics;

import java.util.Objects;

/** Immutable environment supplied by the caller of a future measurement. */
public record PerformanceEnvironment(
        String javaVersion,
        String jvmVendor,
        String osName,
        String osVersion,
        String osArchitecture,
        int availableProcessors,
        long configuredMaxHeapBytes
) {
    public PerformanceEnvironment {
        javaVersion = required(javaVersion, "javaVersion");
        jvmVendor = required(jvmVendor, "jvmVendor");
        osName = required(osName, "osName");
        osVersion = required(osVersion, "osVersion");
        osArchitecture = required(osArchitecture, "osArchitecture");

        if (availableProcessors <= 0) {
            throw new IllegalArgumentException("availableProcessors must be positive");
        }
        if (configuredMaxHeapBytes <= 0) {
            throw new IllegalArgumentException("configuredMaxHeapBytes must be positive");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
