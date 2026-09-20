package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateInstallOutcomeStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void consumesSuccessfulOutcomeOnce() throws Exception {
        Path file = temporaryDirectory.resolve("result.properties");
        Files.writeString(file, """
                status=SUCCESS
                version=1.1.0
                reason=SUCCESS
                exitCode=0
                """);

        UpdateInstallOutcomeStore store =
                new UpdateInstallOutcomeStore(file);
        Optional<UpdateInstallOutcome> outcome = store.consume();

        assertTrue(outcome.isPresent());
        assertEquals(
                UpdateInstallOutcome.Status.SUCCESS,
                outcome.orElseThrow().status()
        );
        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                outcome.orElseThrow().version()
        );
        assertFalse(Files.exists(file));
        assertTrue(store.consume().isEmpty());
    }

    @Test
    void malformedOutcomeIsIgnoredAndRemoved() throws Exception {
        Path file = temporaryDirectory.resolve("result.properties");
        Files.writeString(file, "status=NOT_A_STATUS\n");

        UpdateInstallOutcomeStore store =
                new UpdateInstallOutcomeStore(file);

        assertTrue(store.consume().isEmpty());
        assertFalse(Files.exists(file));
    }
}
