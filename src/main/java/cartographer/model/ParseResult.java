package cartographer.model;

import java.util.Optional;

public final class ParseResult<T> {
    private final T value;
    private final String error;

    private ParseResult(T value, String error) {
        this.value = value;
        this.error = error;
    }

    public static <T> ParseResult<T> success(T value) {
        return new ParseResult<>(value, null);
    }

    public static <T> ParseResult<T> failure(String error) {
        return new ParseResult<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public Optional<String> error() {
        return Optional.ofNullable(error);
    }
}
