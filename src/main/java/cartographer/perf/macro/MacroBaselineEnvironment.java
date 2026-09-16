package cartographer.perf.macro;

import cartographer.perf.metrics.PerformanceEnvironment;

/** Captures the actual JVM environment for one reviewer-run baseline. */
public final class MacroBaselineEnvironment {
    private MacroBaselineEnvironment() {
    }

    public static PerformanceEnvironment capture() {
        Runtime runtime = Runtime.getRuntime();
        return new PerformanceEnvironment(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                runtime.availableProcessors(),
                runtime.maxMemory()
        );
    }
}
