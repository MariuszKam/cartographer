package cartographer.perf.snapshot;

import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record Pf3RenderSizedValidationReport(
        Pf28SnapshotValidationReport mapSurfaceValidation,
        SaveSafetyResult fullSourceSafety,
        List<Pf3RockWarmRenderSample> rockWarmRenders
) {
    private static final Set<Integer> REQUIRED_RADII =
            Set.of(1024, 2048, 4096);

    public Pf3RenderSizedValidationReport {
        mapSurfaceValidation = Objects.requireNonNull(
                mapSurfaceValidation,
                "mapSurfaceValidation is required"
        );
        fullSourceSafety = Objects.requireNonNull(
                fullSourceSafety,
                "fullSourceSafety is required"
        );
        rockWarmRenders = List.copyOf(Objects.requireNonNull(
                rockWarmRenders,
                "rockWarmRenders are required"
        ));
    }

    public boolean accepted() {
        Set<Integer> radii = rockWarmRenders.stream()
                .map(Pf3RockWarmRenderSample::radius)
                .collect(Collectors.toSet());
        return mapSurfaceValidation.accepted()
                && fullSourceSafety.status() == SaveSafetyStatus.PASS
                && rockWarmRenders.size() == REQUIRED_RADII.size()
                && radii.equals(REQUIRED_RADII)
                && rockWarmRenders.stream().allMatch(
                        Pf3RockWarmRenderSample::accepted
                );
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("PF-3 render-sized integrated validation\n");
        out.append("Candidate SHA: ")
                .append(mapSurfaceValidation.candidateSha())
                .append('\n');
        out.append("Full-campaign source safety: ")
                .append(fullSourceSafety.status())
                .append('\n');
        if (!fullSourceSafety.violations().isEmpty()) {
            out.append("Full-campaign source-safety violations:\n");
            fullSourceSafety.violations().forEach(violation ->
                    out.append("- ")
                            .append(violation.type())
                            .append(": ")
                            .append(violation.path())
                            .append('\n')
            );
        }

        out.append("\n=== Map + Surface (PF-2.8 shared gate) ===\n");
        out.append(mapSurfaceValidation.render());

        out.append("\n=== UPPER_ROCK snapshot-direct gate ===\n");
        for (Pf3RockWarmRenderSample sample : rockWarmRenders) {
            out.append("\nR").append(sample.radius()).append('\n');
            out.append("Warm direct elapsed ns: ")
                    .append(sample.warmElapsedNanoseconds())
                    .append('\n');
            out.append("Exact snapshot parity elapsed ns: ")
                    .append(sample.exactParityElapsedNanoseconds())
                    .append('\n');
            out.append("Warm source connections: ")
                    .append(sample.sourceConnectionsOpened())
                    .append(" opened / ")
                    .append(sample.sourceConnectionsClosed())
                    .append(" closed\n");
            out.append("Retained RockMap absent: ")
                    .append(sample.retainedMapAbsent())
                    .append('\n');
            out.append("Geometry parity: ")
                    .append(sample.geometryParity())
                    .append('\n');
            out.append("Legend parity: ")
                    .append(sample.legendParity())
                    .append('\n');
            out.append("Count parity: ")
                    .append(sample.countParity())
                    .append('\n');
            out.append("Image parity: ")
                    .append(sample.imageParity())
                    .append('\n');
            out.append("Warm fingerprint: ")
                    .append(sample.warmFingerprint().sha256Hex())
                    .append('\n');
            out.append("Exact fingerprint: ")
                    .append(sample.exactFingerprint().sha256Hex())
                    .append('\n');
            appendResources(out, sample.warmResources());
        }

        out.append("\nVerdict: ")
                .append(accepted() ? "PASS" : "FAIL")
                .append('\n');
        return out.toString();
    }

    private static void appendResources(
            StringBuilder out,
            Pf18ResourceEvidence evidence
    ) {
        out.append(evidence.render(
                "Warm ROCK process CPU ns",
                evidence.processCpuNanoseconds(),
                evidence.cpuMethod()
        )).append('\n');
        out.append(evidence.render(
                "Warm ROCK peak heap bytes",
                evidence.peakHeapBytes(),
                evidence.heapMethod()
        )).append('\n');
        out.append(evidence.render(
                "Warm ROCK GC count",
                evidence.gcCollectionCount(),
                evidence.gcMethod()
        )).append('\n');
        out.append(evidence.render(
                "Warm ROCK GC time ms",
                evidence.gcCollectionTimeMilliseconds(),
                evidence.gcMethod()
        )).append('\n');
    }
}
