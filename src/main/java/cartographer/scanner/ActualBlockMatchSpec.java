package cartographer.scanner;

import java.util.Objects;

public record ActualBlockMatchSpec(
        String match,
        ActualBlockMatchMode mode
) {
    public ActualBlockMatchSpec {
        if (match == null || match.isBlank()) {
            throw new IllegalArgumentException(
                    "match must not be blank"
            );
        }
        Objects.requireNonNull(mode, "mode is required");
    }
}
