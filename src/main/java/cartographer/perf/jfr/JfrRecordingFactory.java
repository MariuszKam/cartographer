package cartographer.perf.jfr;

@FunctionalInterface
public interface JfrRecordingFactory {
    JfrRecordingController open(JfrRecordingPlan plan);
}
