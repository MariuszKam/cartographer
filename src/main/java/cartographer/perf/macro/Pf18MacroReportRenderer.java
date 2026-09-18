package cartographer.perf.macro;

import java.util.Objects;

/** Deterministic text renderer for PF-1.8 macro evidence. */
public final class Pf18MacroReportRenderer {
    public String render(Pf18MacroReport report) {
        Objects.requireNonNull(report, "report is required");
        StringBuilder out = new StringBuilder();
        line(out, "VS Cartographer PF-1.8 Macro Evidence");
        line(out, "=====================================");
        line(out, "Git SHA: " + report.gitSha());
        line(out, "Save fingerprint: " + report.saveFingerprint());
        line(out, "Normalized source path: " + report.savePath());
        line(out, "Workload: " + report.workloadId());
        line(out, "Family: " + report.workloadFamily());
        line(out, "Radius: R" + report.radius());
        line(out, "Workload contract: " + report.workloadContract());
        line(out, "Execution mode: " + report.executionMode());
        line(out, "Preparation: " + report.preparation());
        line(out, "OS filesystem cache state: uncontrolled");
        line(out, "Java: " + report.environment().javaVersion());
        line(out, "JVM vendor: " + report.environment().jvmVendor());
        line(out, "OS: " + report.environment().osName() + " "
                + report.environment().osVersion());
        line(out, "Architecture: " + report.environment().osArchitecture());
        line(out, "Available processors: " + report.environment().availableProcessors());
        line(out, "Configured max heap bytes: "
                + report.environment().configuredMaxHeapBytes());
        line(out, "Warmups: " + report.warmupCount());
        line(out, "Measured iterations: " + report.measuredCount());
        line(out, "Semantic fingerprint: "
                + report.semanticFingerprint().orElse("UNAVAILABLE"));
        line(out, "Image fingerprint: "
                + report.imageFingerprint().orElse("UNAVAILABLE"));
        line(out, "Resource evidence samples: " + report.resourceEvidence().size());
        for (int i = 0; i < report.resourceEvidence().size(); i++) {
            var resource = report.resourceEvidence().get(i);
            line(out, "Resource " + i + " CPU: " + resource.render(
                    "CPU", resource.processCpuNanoseconds(), resource.cpuMethod()));
            line(out, "Resource " + i + " peak heap: " + resource.render(
                    "peak heap", resource.peakHeapBytes(), resource.heapMethod()));
            line(out, "Resource " + i + " GC count: " + resource.render(
                    "GC count", resource.gcCollectionCount(), resource.gcMethod()));
            line(out, "Resource " + i + " GC time ms: " + resource.render(
                    "GC time ms", resource.gcCollectionTimeMilliseconds(), resource.gcMethod()));
            line(out, "Resource " + i + " allocation: " + resource.render(
                    "allocation", resource.allocatedBytes(), resource.allocationMethod()));
            line(out, "Resource " + i + " RSS: " + resource.render(
                    "RSS", resource.rssBytes(), resource.rssMethod()));
        }
        line(out, "Min: " + duration(report.minNanoseconds()));
        line(out, "P50: " + duration(report.p50Nanoseconds()));
        line(out, "P95: " + duration(report.p95Nanoseconds()));
        line(out, "Max: " + duration(report.maxNanoseconds()));
        line(out, "Source safety: " + report.sourceSafety().map(value -> value.status().name())
                .orElse("INCONCLUSIVE (inspection unavailable)"));
        report.sourceSafetyInspectionFailure().ifPresent(failure ->
                line(out, "Source safety inspection failure: " + failure));
        line(out, "BEFORE source: " + fileState(report.beforeSafety().mainSave()));
        line(out, "BEFORE WAL: " + fileState(report.beforeSafety().wal()));
        line(out, "BEFORE SHM: " + fileState(report.beforeSafety().shm()));
        line(out, "BEFORE journal: " + fileState(report.beforeSafety().journal()));
        line(out, "AFTER safety captured: " + report.afterSafety().isPresent());
        report.afterSafety().ifPresent(after -> {
            line(out, "AFTER source: " + fileState(after.mainSave()));
            line(out, "AFTER WAL: " + fileState(after.wal()));
            line(out, "AFTER SHM: " + fileState(after.shm()));
            line(out, "AFTER journal: " + fileState(after.journal()));
        });
        if (report.sourceSafety().isPresent()
                && !report.sourceSafety().orElseThrow().violations().isEmpty()) {
            line(out, "Safety violations:");
            report.sourceSafety().orElseThrow().violations().forEach(violation ->
                    line(out, "- " + violation.type() + ": " + violation.path()));
        }
        line(out, "Cache HIT verified: " + report.cacheHitVerified());
        line(out, "Evidence verdict: " + (report.evidenceIsValid() ? "FACTUAL" : "INVALID"));
        line(out, "Completed work unit: one declared Cartographer render/analysis operation");
        line(out, "Throughput: not reported");
        line(out, "Measured samples:");
        for (int i = 0; i < report.measuredWallClockNanoseconds().size(); i++) {
            line(out, i + ": " + report.measuredWallClockNanoseconds().get(i) + " ns");
        }
        line(out, "Per-iteration operation evidence:");
        report.measuredEvidence().forEach(evidence -> line(out,
                evidence.iterationIndex() + ": successful=" + evidence.successful()
                        + ", cacheHit=" + evidence.cacheHit().map(Object::toString).orElse("UNAVAILABLE")
                        + ", sourceWork=" + evidence.sourceWork().orElse("UNAVAILABLE")
                        + ", failure=" + evidence.failure().orElse("none")));
        line(out, "Cache evidence:");
        report.cacheEvidence().forEach(value -> line(out, "- " + value));
        line(out, "Failures:");
        if (report.failures().isEmpty()) {
            line(out, "- none");
        } else {
            report.failures().forEach(value -> line(out, "- " + value));
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String value) {
        out.append(value).append('\n');
    }

    private static String duration(java.util.OptionalLong value) {
        return value.isPresent() ? value.getAsLong() + " ns" : "UNAVAILABLE";
    }

    private static String fileState(cartographer.perf.safety.SaveFileSnapshot file) {
        return file.path() + ", exists=" + file.exists() + ", size="
                + (file.sizeBytes().isPresent() ? file.sizeBytes().getAsLong() : "UNAVAILABLE")
                + ", mtime=" + file.lastModified().map(Object::toString).orElse("UNAVAILABLE")
                + ", sha256=" + file.sha256().map(value -> value.sha256Hex())
                .orElse("UNAVAILABLE");
    }
}
