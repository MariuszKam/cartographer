package cartographer.perf.safety;

import java.nio.file.Path;

/** Opt-in PF-1.8 source-safety command; it is not benchmark evidence. */
public final class Pf18SourceSafetyMain {
    private Pf18SourceSafetyMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: pf18SourceSafety <path-to-world.vcdbs> <cache-root>");
        }
        Pf18SourceSafetyReport report = new Pf18SourceSafetyRunner().validate(
                Path.of(args[0]), Path.of(args[1]));
        System.out.println("PF-1.8 source safety: " + report.status());
        System.out.println("Save: " + report.savePath());
        System.out.println("Cache root: " + report.cacheRoot());
        System.out.println("Workload: " + report.workload());
        System.out.println("Operation completed: " + report.operationCompleted());
        Pf18SourceSafetyReport.Pf18CacheEvidence evidence = report.cacheEvidence();
        System.out.println("PF-1.7 manifest present: " + evidence.manifestPresent());
        System.out.println("PF-1.7 manifest qualifying: " + evidence.qualifyingManifest());
        System.out.println("Terrain cache present: " + evidence.terrainCachePresent());
        System.out.println("Surface cache present: " + evidence.surfaceCachePresent());
        System.out.println("PF-1.7 artifacts contained: " + evidence.contained());
        evidence.artifactPaths().forEach(path -> System.out.println("Artifact: " + path));
        report.saveSafety().ifPresent(result -> {
            System.out.println("Source safety: " + result.status());
            if (result.violations().isEmpty()) {
                System.out.println("Violations: none");
            } else {
                result.violations().forEach(violation ->
                        System.out.println("- " + violation.type() + ": " + violation.path()));
            }
        });
        report.failure().ifPresent(failure -> System.out.println("Failure: " + failure));
        if (!report.accepted()) {
            throw new IllegalStateException("PF-1.8 source-safety operation did not pass");
        }
    }
}
