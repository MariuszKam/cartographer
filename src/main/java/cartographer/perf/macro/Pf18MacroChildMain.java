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
        long started = System.nanoTime();
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
        properties.put("operationNanoseconds", Long.toString(System.nanoTime() - started));
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
                optional(properties.getProperty("semantic")),
                optional(properties.getProperty("image")),
                Boolean.parseBoolean(required(properties, "cacheHit")),
                required(properties, "sourceWork"));
    }

    static Pf18ResourceEvidence readResourceEvidence(Path path) throws IOException {
        Map<String, String> properties = Pf18DeterministicEvidenceCodec.read(path);
        return new Pf18ResourceEvidence(
                optionalLong(properties.getProperty("cpu")),
                optionalLong(properties.getProperty("heap")),
                optionalLong(properties.getProperty("gcCount")),
                optionalLong(properties.getProperty("gcTime")),
                optionalLong(properties.getProperty("allocation")),
                optionalLong(properties.getProperty("rss")),
                "child JVM OperatingSystemMXBean", "child JVM heap pools",
                "child JVM GC beans", "UNAVAILABLE", "UNAVAILABLE");
    }

    private static void writeOptional(Map<String, String> properties, String name,
                                      java.util.OptionalLong value) {
        properties.put(name, value.isPresent() ? Long.toString(value.getAsLong()) : "UNAVAILABLE");
    }

    private static java.util.OptionalLong optionalLong(String value) {
        if (value == null || value.equals("UNAVAILABLE")) return java.util.OptionalLong.empty();
        return java.util.OptionalLong.of(Long.parseLong(value));
    }

    private static Optional<String> optional(String value) {
        return value == null || value.equals("UNAVAILABLE") ? Optional.empty() : Optional.of(value);
    }

    private static String required(Map<String, String> properties, String name) {
        return Objects.requireNonNull(properties.get(name), name + " is missing");
    }
}
