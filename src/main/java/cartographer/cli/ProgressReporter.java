package cartographer.cli;

import java.io.PrintStream;

public class ProgressReporter extends cartographer.application.ProgressReporter {

    private final PrintStream out;

    private String currentStage =
            "";

    private int lastBucket =
            -1;

    public ProgressReporter(
            PrintStream out
    ) {
        this.out =
                out;
    }

    @Override
    public void start(
            String stage
    ) {
        if (out == null) {
            return;
        }

        currentStage =
                stage;

        lastBucket =
                -1;

        out.println(
                "  0% - "
                        + stage
        );
    }

    @Override
    public void progress(
            String stage,
            int current,
            int total
    ) {
        if (out == null
                || total <= 0) {

            return;
        }

        if (!stage.equals(
                currentStage
        )) {
            currentStage =
                    stage;

            lastBucket =
                    -1;
        }

        long scaledCurrent =
                (long) current
                        * 100L;

        int percent =
                (int) Math.clamp(
                        scaledCurrent
                                / (long) total
                        ,
                        0L,
                        100L);

        int bucket =
                percent
                        / 10;

        if (bucket
                != lastBucket) {

            lastBucket =
                    bucket;

            out.printf(
                    "%3d%% - %s (%d/%d)%n",
                    percent,
                    stage,
                    current,
                    total
            );
        }
    }

    @Override
    public void done(
            String stage
    ) {
        if (out == null) {
            return;
        }

        out.println(
                "100% - "
                        + stage
        );
    }
}
