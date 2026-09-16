package cartographer.perf.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealSaveValidationRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void unchangedProtectedFilesPass() throws Exception {
        Path save = createSave();

        SaveSafetyResult result = runner(path -> { }).validate(save);

        assertEquals(SaveSafetyStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void operationCreatedWalFailsAndLeavesEvidenceInPlace() throws Exception {
        Path save = createSave();
        Path wal = sidecar(save, "-wal");

        SaveSafetyResult result = runner(path -> Files.writeString(wal, "wal", StandardCharsets.UTF_8))
                .validate(save);

        assertEquals(SaveSafetyStatus.FAIL, result.status());
        assertTrue(result.violations().stream()
                .anyMatch(violation -> violation.type() == SaveSafetyViolationType.WAL_CREATED));
        assertTrue(Files.exists(wal));
    }

    @Test
    void operationCreatedShmFails() throws Exception {
        Path save = createSave();
        Path shm = sidecar(save, "-shm");

        SaveSafetyResult result = runner(path -> Files.writeString(shm, "shm", StandardCharsets.UTF_8))
                .validate(save);

        assertEquals(SaveSafetyStatus.FAIL, result.status());
        assertTrue(result.violations().stream()
                .anyMatch(violation -> violation.type() == SaveSafetyViolationType.SHM_CREATED));
    }

    @Test
    void operationMainSaveMutationFails() throws Exception {
        Path save = createSave();

        SaveSafetyResult result = runner(path ->
                Files.writeString(path, "changed", StandardCharsets.UTF_8)).validate(save);

        assertEquals(SaveSafetyStatus.FAIL, result.status());
        assertTrue(result.violations().stream().anyMatch(
                violation -> violation.type() == SaveSafetyViolationType.SAVE_CONTENT_CHANGED));
    }

    @Test
    void unchangedPreExistingSidecarRemainsAllowed() throws Exception {
        Path save = createSave();
        Files.writeString(sidecar(save, "-wal"), "existing", StandardCharsets.UTF_8);

        SaveSafetyResult result = runner(path -> { }).validate(save);

        assertEquals(SaveSafetyStatus.PASS, result.status());
    }

    private RealSaveValidationRunner runner(RealSaveValidationRunner.ReadOnlyOperation operation) {
        return new RealSaveValidationRunner(
                new SaveSafetySnapshotter(), new SaveSafetyGate(), operation);
    }

    private Path createSave() throws Exception {
        Path save = Files.createTempFile(temporaryDirectory, "save-", ".vcdbs");
        Files.writeString(save, "save", StandardCharsets.UTF_8);
        return save;
    }

    private static Path sidecar(Path save, String suffix) {
        return save.resolveSibling(save.getFileName() + suffix);
    }
}
