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
    void consumesRestartRequiredOutcome() throws Exception {
        Path file = temporaryDirectory.resolve("restart-required.properties");
        Files.writeString(file, """
                status=RESTART_REQUIRED
                version=1.1.0
                reason=RESTART_REQUIRED
                exitCode=3010
                """);

        UpdateInstallOutcomeStore store =
                new UpdateInstallOutcomeStore(file);
        UpdateInstallOutcome outcome = store.consume().orElseThrow();

        assertEquals(
                UpdateInstallOutcome.Status.RESTART_REQUIRED,
                outcome.status()
        );
        assertEquals(3010, outcome.exitCode());
        assertFalse(Files.exists(file));
    }

    @Test
    void consumesRebootInitiatedAsSuccessfulOutcome() throws Exception {
        Path file = temporaryDirectory.resolve("reboot-initiated.properties");
        Files.writeString(file, """
                status=SUCCESS
                version=1.1.0
                reason=SUCCESS
                exitCode=1641
                """);

        UpdateInstallOutcomeStore store =
                new UpdateInstallOutcomeStore(file);
        UpdateInstallOutcome outcome = store.consume().orElseThrow();

        assertEquals(UpdateInstallOutcome.Status.SUCCESS, outcome.status());
        assertEquals(1641, outcome.exitCode());
        assertFalse(Files.exists(file));
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
