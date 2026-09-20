package cartographer.update;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePreferencesTest {

    @Test
    void defaultPreferencesCheckAutomatically() {
        assertTrue(
                UpdatePreferences.defaults().shouldCheckAutomatically(
                        Instant.parse("2026-09-20T10:00:00Z"),
                        Duration.ofHours(24)
                )
        );
    }

    @Test
    void successfulCheckSuppressesAutomaticCheckUntilTtlExpires() {
        Instant last = Instant.parse("2026-09-20T10:00:00Z");
        UpdatePreferences preferences = new UpdatePreferences(
                true,
                Optional.of(last)
        );

        assertFalse(
                preferences.shouldCheckAutomatically(
                        last.plus(Duration.ofHours(23)),
                        Duration.ofHours(24)
                )
        );
        assertTrue(
                preferences.shouldCheckAutomatically(
                        last.plus(Duration.ofHours(24)),
                        Duration.ofHours(24)
                )
        );
    }

    @Test
    void disabledAutoCheckAndFutureTimestampsAreHandledSafely() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        assertFalse(
                new UpdatePreferences(false, Optional.empty())
                        .shouldCheckAutomatically(
                                now,
                                Duration.ofHours(24)
                        )
        );
        assertTrue(
                new UpdatePreferences(
                        true,
                        Optional.of(now.plus(Duration.ofHours(1)))
                ).shouldCheckAutomatically(
                        now,
                        Duration.ofHours(24)
                )
        );
    }
}
