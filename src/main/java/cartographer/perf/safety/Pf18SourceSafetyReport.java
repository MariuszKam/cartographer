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
        Pf18CacheEvidence cacheEvidence,
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
        cacheEvidence = Objects.requireNonNull(cacheEvidence, "cache evidence is required");
        failure = Objects.requireNonNull(failure, "failure is required");
        if (status == Pf18SourceSafetyStatus.PASS
                && (!operationCompleted || saveSafety.isEmpty()
                || saveSafety.orElseThrow().status() != SaveSafetyStatus.PASS
                || !cacheEvidence.qualifyingManifest()
                || !cacheEvidence.contained())) {
            throw new IllegalArgumentException("PF-1.8 PASS requires completed safe operation");
        }
    }

    public boolean accepted() {
        return status == Pf18SourceSafetyStatus.PASS;
    }

    public record Pf18CacheEvidence(
            boolean manifestPresent,
            boolean qualifyingManifest,
            boolean terrainCachePresent,
            boolean surfaceCachePresent,
            boolean contained,
            List<Path> artifactPaths
    ) {
        public Pf18CacheEvidence {
            artifactPaths = List.copyOf(Objects.requireNonNull(artifactPaths, "artifact paths are required"))
                    .stream()
                    .map(path -> Objects.requireNonNull(path, "artifact path is required")
                            .toAbsolutePath().normalize())
                    .toList();
        }

    }
}
