package cartographer.perf.benchmark;

import java.util.Objects;

/** Compact failure data; Throwable object graphs and stack traces are not retained. */
public record BenchmarkFailure(
        String exceptionType,
        String message,
        String context
) {
    public BenchmarkFailure {
        exceptionType = required(exceptionType, "exceptionType");
        message = message == null ? "" : message;
        context = context == null ? "" : context;
    }

    public static BenchmarkFailure from(Throwable failure, String context) {
        Objects.requireNonNull(failure, "failure is required");
        return new BenchmarkFailure(
                failure.getClass().getName(),
                failure.getMessage(),
                context
        );
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
