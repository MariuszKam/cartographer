package cartographer.perf.macro;

import cartographer.perf.workload.WorkloadSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** One-operation child entry point for genuine PROCESS_COLD evidence. */
public final class Pf18MacroChildMain {
    private Pf18MacroChildMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException("Usage: child <save> <cacheRoot> <workload> <evidence>");
        }
        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        Path cache = Path.of(args[1]).toAbsolutePath().normalize();
        WorkloadSpec workload = MacroWorkloadResolver.resolve(args[2]);
        Path evidence = Path.of(args[3]).toAbsolutePath().normalize();
        long started = System.nanoTime();
        Pf18IterationEvidence result = new Pf18ProductionOperationFactory(
                evidence.getParent().resolve("child-state")).create(save, cache, workload).execute();
        Properties properties = new Properties();
        properties.setProperty("semantic", result.semanticFingerprint().orElse("UNAVAILABLE"));
        properties.setProperty("image", result.imageFingerprint().orElse("UNAVAILABLE"));
        properties.setProperty("cacheHit", Boolean.toString(result.cacheHit()));
        properties.setProperty("sourceWork", result.sourceWork());
        properties.setProperty("operationNanoseconds", Long.toString(System.nanoTime() - started));
        try {
            Files.createDirectories(evidence.getParent());
            try (OutputStream output = Files.newOutputStream(evidence)) {
                properties.store(output, "PF-1.8 machine-readable child evidence");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write child evidence", exception);
        }
    }

    static Pf18IterationEvidence readEvidence(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new Pf18IterationEvidence(
                optional(properties.getProperty("semantic")),
                optional(properties.getProperty("image")),
                Boolean.parseBoolean(required(properties, "cacheHit")),
                required(properties, "sourceWork"));
    }

    private static Optional<String> optional(String value) {
        return value == null || value.equals("UNAVAILABLE") ? Optional.empty() : Optional.of(value);
    }

    private static String required(Properties properties, String name) {
        return Objects.requireNonNull(properties.getProperty(name), name + " is missing");
    }
}
