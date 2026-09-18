package cartographer.perf.metrics;

import java.util.Objects;
import java.util.OptionalLong;

/** Optional resource facts for one measured operation; absent means unavailable. */
public record Pf18ResourceEvidence(
        OptionalLong processCpuNanoseconds,
        OptionalLong peakHeapBytes,
        OptionalLong gcCollectionCount,
        OptionalLong gcCollectionTimeMilliseconds,
        OptionalLong allocatedBytes,
        OptionalLong rssBytes,
        String cpuMethod,
        String heapMethod,
        String gcMethod,
        String allocationMethod,
        String rssMethod
) {
    public Pf18ResourceEvidence {
        processCpuNanoseconds = Objects.requireNonNull(processCpuNanoseconds);
        peakHeapBytes = Objects.requireNonNull(peakHeapBytes);
        gcCollectionCount = Objects.requireNonNull(gcCollectionCount);
        gcCollectionTimeMilliseconds = Objects.requireNonNull(gcCollectionTimeMilliseconds);
        allocatedBytes = Objects.requireNonNull(allocatedBytes);
        rssBytes = Objects.requireNonNull(rssBytes);
        cpuMethod = required(cpuMethod, "cpuMethod");
        heapMethod = required(heapMethod, "heapMethod");
        gcMethod = required(gcMethod, "gcMethod");
        allocationMethod = required(allocationMethod, "allocationMethod");
        rssMethod = required(rssMethod, "rssMethod");
    }

    public static Pf18ResourceEvidence unavailable() {
        return new Pf18ResourceEvidence(OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
                OptionalLong.empty(), "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE",
                "UNAVAILABLE", "UNAVAILABLE");
    }

    public String render(String name, OptionalLong value, String method) {
        return name + ": " + (value.isPresent() ? value.getAsLong() : "UNAVAILABLE")
                + " (method: " + method + ")";
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        return value.isBlank() ? "UNAVAILABLE" : value.trim();
    }
}
