package cartographer.perf.workload;

import java.util.Locale;
import java.util.Objects;

/** Deterministic, locale-independent identity for a selected resource. */
public record ResourceIdentity(String value) {
    public ResourceIdentity {
        Objects.requireNonNull(value, "resource value is required");
        value = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("resource value must not be blank");
        }
    }

    public static ResourceIdentity of(String value) {
        return new ResourceIdentity(value);
    }
}
