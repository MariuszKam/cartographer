package cartographer.perf.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18SourceSafetyRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsCacheRootInsideOrBesideTheProtectedSaveDirectory() throws Exception {
        Path save = createSave();
        assertThrows(IllegalArgumentException.class,
                () -> new Pf18SourceSafetyRunner().validate(save, save.getParent()));
        assertThrows(IllegalArgumentException.class,
                () -> new Pf18SourceSafetyRunner().validate(save, save));
    }

    @Test
    void propagatesSafeResultOnlyWhenOperationCompleted() throws Exception {
        Path save = createSave();
        Path cache = temporaryDirectory.resolve("safe-cache");
        Pf18SourceSafetyReport report = runner((path, root) -> {
            Files.createDirectories(root);
            Files.writeString(root.resolve("artifact.txt"), "cache");
        }).validate(save, cache);

        assertEquals(Pf18SourceSafetyStatus.PASS, report.status());
        assertTrue(report.accepted());
        assertTrue(report.cacheArtifactsProduced());
        assertTrue(report.cacheArtifacts().stream().allMatch(
                path -> path.startsWith(cache.toAbsolutePath().normalize())));
    }

    @Test
    void sourceMutationAndWalCreationAreFailures() throws Exception {
        Path save = createSave();
        Path cache = temporaryDirectory.resolve("mutation-cache");
        Pf18SourceSafetyReport mutation = runner((path, root) ->
                Files.writeString(path, "changed")).validate(save, cache);
        assertEquals(Pf18SourceSafetyStatus.FAIL, mutation.status());
        assertFalse(mutation.accepted());

        Path secondSave = createSave();
        Pf18SourceSafetyReport wal = runner((path, root) ->
                Files.writeString(path.resolveSibling(path.getFileName() + "-wal"), "wal"))
                .validate(secondSave, temporaryDirectory.resolve("wal-cache"));
        assertEquals(Pf18SourceSafetyStatus.FAIL, wal.status());
        assertFalse(wal.accepted());
    }

    @Test
    void failedOperationDoesNotBecomePass() throws Exception {
        Path save = createSave();
        Pf18SourceSafetyReport report = runner((path, root) ->
                { throw new IllegalStateException("operation failed"); })
                .validate(save, temporaryDirectory.resolve("failed-cache"));

        assertEquals(Pf18SourceSafetyStatus.FAIL, report.status());
        assertFalse(report.accepted());
        assertFalse(report.operationCompleted());
        assertTrue(report.failure().isPresent());
    }

    private Pf18SourceSafetyRunner runner(Pf18SourceSafetyRunner.SafetyOperation operation) {
        return new Pf18SourceSafetyRunner(
                new SaveSafetySnapshotter(), new SaveSafetyGate(), operation);
    }

    private Path createSave() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("source");
        Files.createDirectories(sourceDirectory);
        Path save = Files.createTempFile(sourceDirectory, "save-", ".vcdbs");
        Files.writeString(save, "save");
        return save;
    }
}
