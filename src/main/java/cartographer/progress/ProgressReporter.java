package cartographer.progress;

public interface ProgressReporter {
    ProgressReporter NONE = new ProgressReporter() {
    };

    default void start(String stage) {
    }

    default void progress(String stage, int current, int total) {
    }

    default void done(String stage) {
    }
}
