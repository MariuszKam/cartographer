package cartographer.perf.safety;

import java.util.Optional;

/** Failure of the real-save validation operation or its safety evidence. */
public final class RealSaveValidationException extends RuntimeException {
    private final SaveSafetyResult safetyResult;

    RealSaveValidationException(String message, Throwable cause, SaveSafetyResult safetyResult) {
        super(message, cause);
        this.safetyResult = safetyResult;
    }

    public Optional<SaveSafetyResult> safetyResult() {
        return Optional.ofNullable(safetyResult);
    }
}
