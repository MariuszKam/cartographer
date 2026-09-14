package cartographer.application;

public class ProgressReporter {
    public static final ProgressReporter NONE = new ProgressReporter();

    public void start(String stage) {
    }

    public void progress(String stage, int current, int total) {
    }

    public void done(String stage) {
    }
}
