package cartographer.perf.jfr;

public class JfrProfilingException extends RuntimeException {
    public JfrProfilingException(String message, Throwable cause) {
        super(message, cause);
    }

    public JfrProfilingException(String message) {
        super(message);
    }
}
