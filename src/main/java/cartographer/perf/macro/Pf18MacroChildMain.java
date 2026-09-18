package cartographer.perf.macro;

import cartographer.perf.workload.WorkloadSpec;
import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.metrics.Pf18ResourceSampler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-operation child entry point for genuine PROCESS_COLD evidence. */
public final class Pf18MacroChildMain {
    private Pf18MacroChildMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: child <save> <cacheRoot> <workload> <cacheMode> <evidence>");
        }
        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        Path cache = Path.of(args[1]).toAbsolutePath().normalize();
        WorkloadSpec workload = MacroWorkloadResolver.resolve(args[2]);
        Pf18CacheMode cacheMode = Pf18CacheMode.valueOf(args[3]);
        Path evidence = Path.of(args[4]).toAbsolutePath().normalize();
        Pf18ResourceSampler.Measured<Pf18IterationEvidence> measured = new Pf18ResourceSampler()
                .measure(new Pf18ProductionOperationFactory(
                        evidence.getParent().resolve("child-state"))
                        .create(save, cacheMode == Pf18CacheMode.ENABLED ? cache : null,
                                workload)::execute);
        Pf18IterationEvidence result = measured.result();
        Pf18ResourceEvidence resources = measured.evidence();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("semantic", result.semanticFingerprint().orElse("UNAVAILABLE"));
        properties.put("image", result.imageFingerprint().orElse("UNAVAILABLE"));
        properties.put("cacheHit", Boolean.toString(result.cacheHit()));
        properties.put("sourceWork", result.sourceWork());
        writeOptional(properties, "cpu", resources.processCpuNanoseconds());
        writeOptional(properties, "heap", resources.peakHeapBytes());
        writeOptional(properties, "gcCount", resources.gcCollectionCount());
        writeOptional(properties, "gcTime", resources.gcCollectionTimeMilliseconds());
        writeOptional(properties, "allocation", resources.allocatedBytes());
        writeOptional(properties, "rss", resources.rssBytes());
        try {
            Pf18DeterministicEvidenceCodec.write(evidence, properties);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write child evidence", exception);
        }
    }

    static Pf18IterationEvidence readEvidence(Path path) throws IOException {
        Map<String, String> properties = Pf18DeterministicEvidenceCodec.read(path);
        return new Pf18IterationEvidence(
                optional(required(properties, "semantic")),
                optional(required(properties, "image")),
                parseBoolean(required(properties, "cacheHit"), "cacheHit"),
                required(properties, "sourceWork"));
    }

    static Pf18ResourceEvidence readResourceEvidence(Path path) throws IOException {
        Map<String, String> properties = Pf18DeterministicEvidenceCodec.read(path);
        return new Pf18ResourceEvidence(
                optionalLong(required(properties, "cpu")),
                optionalLong(required(properties, "heap")),
                optionalLong(required(properties, "gcCount")),
                optionalLong(required(properties, "gcTime")),
                optionalLong(required(properties, "allocation")),
                optionalLong(required(properties, "rss")),
                "child JVM OperatingSystemMXBean", "child JVM heap pools",
                "child JVM GC beans", "UNAVAILABLE", "UNAVAILABLE");
    }

    private static void writeOptional(Map<String, String> properties, String name,
                                      java.util.OptionalLong value) {
        properties.put(name, value.isPresent() ? Long.toString(value.getAsLong()) : "UNAVAILABLE");
    }

    private static java.util.OptionalLong optionalLong(String value) {
        if (value.equals("UNAVAILABLE")) return java.util.OptionalLong.empty();
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new IllegalArgumentException("negative child evidence value");
            return java.util.OptionalLong.of(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid child evidence number: " + value, exception);
        }
    }

    private static Optional<String> optional(String value) {
        return value.equals("UNAVAILABLE") ? Optional.empty() : Optional.of(value);
    }

    private static String required(Map<String, String> properties, String name) {
        String value = Objects.requireNonNull(properties.get(name), name + " is missing");
        if (value.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    private static boolean parseBoolean(String value, String name) {
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException(name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
