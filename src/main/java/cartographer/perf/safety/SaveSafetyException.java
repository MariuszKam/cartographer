package cartographer.perf.safety;

/** Clear failure while capturing protected save filesystem evidence. */
public class SaveSafetyException extends RuntimeException {
    public SaveSafetyException(String message) {
        super(message);
    }

    public SaveSafetyException(String message, Throwable cause) {
        super(message, cause);
    }
}
