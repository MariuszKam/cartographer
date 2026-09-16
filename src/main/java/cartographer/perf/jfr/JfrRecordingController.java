package cartographer.perf.jfr;

import java.nio.file.Path;

/** Small lifecycle boundary around one owned JFR Recording. */
public interface JfrRecordingController extends AutoCloseable {
    void configure();

    void start();

    void stop();

    void dump(Path destination);

    @Override
    void close();
}
