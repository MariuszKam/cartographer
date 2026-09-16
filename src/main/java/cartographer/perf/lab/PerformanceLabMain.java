package cartographer.perf.lab;

/** Java entry point launched only by the local opt-in Gradle task. */
public final class PerformanceLabMain {
    private PerformanceLabMain() {
    }

    public static void main(String[] args) {
        new PerformanceLabRunner().run(PerformanceLabConfiguration.fromArgs(args));
    }
}
