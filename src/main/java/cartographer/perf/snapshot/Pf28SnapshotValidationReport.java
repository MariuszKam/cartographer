package cartographer.perf.snapshot;

import cartographer.perf.metrics.Pf18ResourceEvidence;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record Pf28SnapshotValidationReport(
        String candidateSha,
        String revisionHash,
        boolean snapshotComplete,
        long coldBuildElapsedNanoseconds,
        Pf18ResourceEvidence coldBuildResources,
        SaveSafetyResult sourceSafety,
        boolean revisionInvalidationPassed,
        List<Pf28WarmRenderSample> warmRenders
) {
    private static final Set<Integer> REQUIRED_RADII =
            Set.of(1024, 2048, 4096);

    public Pf28SnapshotValidationReport {
        candidateSha = fullSha(candidateSha);
        revisionHash = required(revisionHash, "revisionHash");
        if (coldBuildElapsedNanoseconds < 0L) {
            throw new IllegalArgumentException(
                    "coldBuildElapsedNanoseconds cannot be negative"
            );
        }
        coldBuildResources = Objects.requireNonNull(
                coldBuildResources,
                "coldBuildResources is required"
        );
        sourceSafety = Objects.requireNonNull(
                sourceSafety,
                "sourceSafety is required"
        );
        warmRenders = List.copyOf(
                Objects.requireNonNull(warmRenders, "warmRenders are required")
        );
    }

    public boolean accepted() {
        Set<Integer> radii = warmRenders.stream()
                .map(Pf28WarmRenderSample::radius)
                .collect(Collectors.toSet());
        return snapshotComplete
                && sourceSafety.status() == SaveSafetyStatus.PASS
                && revisionInvalidationPassed
                && warmRenders.size() == REQUIRED_RADII.size()
                && radii.equals(REQUIRED_RADII)
                && warmRenders.stream().allMatch(
                        Pf28WarmRenderSample::accepted
                );
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("PF-2.8 cold-ingest / warm-render validation\n");
        out.append("Candidate SHA: ").append(candidateSha).append('\n');
        out.append("Snapshot revision: ").append(revisionHash).append('\n');
        out.append("Snapshot complete: ").append(snapshotComplete).append('\n');
        out.append("Cold build elapsed ns: ")
                .append(coldBuildElapsedNanoseconds)
                .append('\n');
        appendResources(out, "Cold", coldBuildResources);
        out.append("Source safety: ")
                .append(sourceSafety.status())
                .append('\n');
        out.append("Revision invalidation: ")
                .append(revisionInvalidationPassed ? "PASS" : "FAIL")
                .append('\n');

        for (Pf28WarmRenderSample sample : warmRenders) {
            out.append("\nR").append(sample.radius()).append('\n');
            out.append("Warm elapsed ns: ")
                    .append(sample.warmElapsedNanoseconds())
                    .append('\n');
            out.append("Source parity elapsed ns: ")
                    .append(sample.sourceParityElapsedNanoseconds())
                    .append('\n');
            out.append("Snapshot-backed: ")
                    .append(sample.snapshotBacked())
                    .append('\n');
            out.append("Warm source connections: ")
                    .append(sample.sourceConnectionsOpened())
                    .append(" opened / ")
                    .append(sample.sourceConnectionsClosed())
                    .append(" closed\n");
            out.append("Terrain snapshot coverage: ")
                    .append(sample.terrainHits())
                    .append(" HIT + ")
                    .append(sample.terrainKnownAbsent())
                    .append(" proven absent / ")
                    .append(sample.terrainRequested())
                    .append(" requested\n");
            out.append("Surface snapshot coverage: ")
                    .append(sample.surfaceHits())
                    .append(" HIT / ")
                    .append(sample.surfaceRequested())
                    .append(" requested\n");
            out.append("Geometry parity: ")
                    .append(sample.geometryParity())
                    .append('\n');
            out.append("Image parity: ")
                    .append(sample.imageParity())
                    .append('\n');
            out.append("Warm fingerprint: ")
                    .append(sample.warmFingerprint().sha256Hex())
                    .append('\n');
            out.append("Source fingerprint: ")
                    .append(sample.sourceFingerprint().sha256Hex())
                    .append('\n');
            appendResources(out, "Warm", sample.warmResources());
        }

        out.append("\nVerdict: ")
                .append(accepted() ? "PASS" : "FAIL")
                .append('\n');
        return out.toString();
    }

    private static void appendResources(
            StringBuilder out,
            String label,
            Pf18ResourceEvidence evidence
    ) {
        out.append(evidence.render(
                label + " process CPU ns",
                evidence.processCpuNanoseconds(),
                evidence.cpuMethod()
        )).append('\n');
        out.append(evidence.render(
                label + " peak heap bytes",
                evidence.peakHeapBytes(),
                evidence.heapMethod()
        )).append('\n');
        out.append(evidence.render(
                label + " GC count",
                evidence.gcCollectionCount(),
                evidence.gcMethod()
        )).append('\n');
        out.append(evidence.render(
                label + " GC time ms",
                evidence.gcCollectionTimeMilliseconds(),
                evidence.gcMethod()
        )).append('\n');
    }

    static String fullSha(String value) {
        String normalized = required(value, "candidateSha")
                .toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(
                    "candidateSha must be a full 40-character SHA"
            );
        }
        return normalized;
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
