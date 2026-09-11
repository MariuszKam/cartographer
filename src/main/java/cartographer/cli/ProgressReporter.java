package cartographer.cli;

import java.io.PrintStream;

public class ProgressReporter {
    public static final ProgressReporter NONE = new ProgressReporter(null);

    private final PrintStream out;
    private String currentStage = "";
    private int lastBucket = -1;

    public ProgressReporter(PrintStream out) {
        this.out = out;
    }

    public void start(String stage) {
        if (out == null) {
            return;
        }
        currentStage = stage;
        lastBucket = -1;
        out.println("  0% - " + stage);
    }

    public void progress(String stage, int current, int total) {
        if (out == null || total <= 0) {
            return;
        }
        if (!stage.equals(currentStage)) {
            currentStage = stage;
            lastBucket = -1;
        }

        int percent = Math.min(100, Math.max(0, (int) Math.round(current * 100.0 / total)));
        int bucket = percent / 10;
        if (bucket != lastBucket || percent == 100) {
            lastBucket = bucket;
            out.printf("%3d%% - %s (%d/%d)%n", percent, stage, current, total);
        }
    }

    public void done(String stage) {
        if (out == null) {
            return;
        }
        out.println("100% - " + stage);
    }
}
