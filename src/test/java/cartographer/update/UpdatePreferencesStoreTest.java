package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePreferencesStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileLoadsDefaults() {
        UpdatePreferences preferences = new UpdatePreferencesStore(
                temporaryDirectory.resolve("update.properties")
        ).load();

        assertTrue(preferences.autoCheck());
        assertTrue(preferences.lastSuccessfulCheck().isEmpty());
    }

    @Test
    void roundTripsPreferences() throws Exception {
        Path path = temporaryDirectory.resolve("update.properties");
        UpdatePreferencesStore store = new UpdatePreferencesStore(path);
        UpdatePreferences expected = new UpdatePreferences(
                false,
                Optional.of(Instant.parse("2026-09-20T10:15:30Z"))
        );

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void malformedTimestampDoesNotDisableAutomaticChecking()
            throws Exception {
        Path path = temporaryDirectory.resolve("update.properties");
        Files.writeString(
                path,
                """
                autoCheck=true
                lastSuccessfulCheck=definitely-not-an-instant
                """,
                StandardCharsets.UTF_8
        );

        UpdatePreferences preferences =
                new UpdatePreferencesStore(path).load();

        assertTrue(preferences.autoCheck());
        assertTrue(preferences.lastSuccessfulCheck().isEmpty());
    }
}
