package cartographer.perf.safety;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Factual report for the opt-in PF-1.8 source-safety workload. */
public record Pf18SourceSafetyReport(
        Path savePath,
        Path cacheRoot,
        String workload,
        Pf18SourceSafetyStatus status,
        Optional<SaveSafetyResult> saveSafety,
        boolean operationCompleted,
        boolean cacheArtifactsProduced,
        List<Path> cacheArtifacts,
        Optional<String> failure
) {
    public Pf18SourceSafetyReport {
        savePath = Objects.requireNonNull(savePath, "save path is required")
                .toAbsolutePath().normalize();
        cacheRoot = Objects.requireNonNull(cacheRoot, "cache root is required")
                .toAbsolutePath().normalize();
        workload = Objects.requireNonNull(workload, "workload is required");
        status = Objects.requireNonNull(status, "status is required");
        saveSafety = Objects.requireNonNull(saveSafety, "save safety is required");
        cacheArtifacts = List.copyOf(Objects.requireNonNull(cacheArtifacts, "cache artifacts are required"));
        failure = Objects.requireNonNull(failure, "failure is required");
        if (status == Pf18SourceSafetyStatus.PASS
                && (!operationCompleted || saveSafety.isEmpty()
                || saveSafety.orElseThrow().status() != SaveSafetyStatus.PASS)) {
            throw new IllegalArgumentException("PF-1.8 PASS requires completed safe operation");
        }
        if (cacheArtifactsProduced != !cacheArtifacts.isEmpty()) {
            throw new IllegalArgumentException("cache artifact flag does not match artifact list");
        }
    }

    public boolean accepted() {
        return status == Pf18SourceSafetyStatus.PASS;
    }
}
