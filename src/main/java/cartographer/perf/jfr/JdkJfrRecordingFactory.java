package cartographer.perf.jfr;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.io.IOException;

/** Creates recordings using only the JDK's built-in JFR configuration. */
public final class JdkJfrRecordingFactory implements JfrRecordingFactory {
    @Override
    public JfrRecordingController open(JfrRecordingPlan plan) {
        try {
            return new JdkJfrRecordingController(
                    new Recording(Configuration.getConfiguration(
                            plan.configuration().jfrName()
                    )),
                    plan
            );
        } catch (IOException exception) {
            throw new JfrProfilingException(
                    "Cannot load JFR configuration " + plan.configuration(),
                    exception
            );
        }
    }

    private static final class JdkJfrRecordingController
            implements JfrRecordingController {
        private final Recording recording;
        private final JfrRecordingPlan plan;

        private JdkJfrRecordingController(Recording recording, JfrRecordingPlan plan) {
            this.recording = recording;
            this.plan = plan;
        }

        @Override
        public void configure() {
            recording.setName(plan.recordingName());
            recording.setMaxSize(plan.maxSizeBytes());
        }

        @Override
        public void start() {
            recording.start();
        }

        @Override
        public void stop() {
            recording.stop();
        }

        @Override
        public void dump(java.nio.file.Path destination) {
            try {
                recording.dump(destination);
            } catch (IOException exception) {
                throw new JfrProfilingException(
                        "Cannot write JFR recording to " + destination,
                        exception
                );
            }
        }

        @Override
        public void close() {
            recording.close();
        }
    }
}
