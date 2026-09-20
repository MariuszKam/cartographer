package cartographer.update;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record UpdatePreferences(
        boolean autoCheck,
        Optional<Instant> lastSuccessfulCheck
) {
    public UpdatePreferences {
        lastSuccessfulCheck = Objects.requireNonNull(
                lastSuccessfulCheck,
                "lastSuccessfulCheck is required"
        );
    }

    public static UpdatePreferences defaults() {
        return new UpdatePreferences(true, Optional.empty());
    }

    public boolean shouldCheckAutomatically(
            Instant now,
            Duration minimumInterval
    ) {
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(
                minimumInterval,
                "minimumInterval is required"
        );
        if (minimumInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "minimumInterval cannot be negative"
            );
        }
        if (!autoCheck) {
            return false;
        }
        if (lastSuccessfulCheck.isEmpty()) {
            return true;
        }

        Instant last = lastSuccessfulCheck.orElseThrow();
        if (last.isAfter(now)) {
            return true;
        }
        return !now.isBefore(last.plus(minimumInterval));
    }

    public UpdatePreferences withSuccessfulCheck(Instant instant) {
        return new UpdatePreferences(
                autoCheck,
                Optional.of(Objects.requireNonNull(
                        instant,
                        "instant is required"
                ))
        );
    }
}
