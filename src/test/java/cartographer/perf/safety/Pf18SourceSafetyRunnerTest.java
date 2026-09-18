package cartographer.perf.safety;

import cartographer.perf.RenderDataCacheRevision;
import cartographer.perf.RenderDataCacheStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
            publishManifest(path, root);
        }).validate(save, cache);

        assertEquals(Pf18SourceSafetyStatus.PASS, report.status());
        assertTrue(report.accepted());
        assertTrue(report.cacheEvidence().manifestPresent());
        assertTrue(report.cacheEvidence().qualifyingManifest());
        assertTrue(report.cacheEvidence().artifactPaths().stream().allMatch(
                path -> path.startsWith(cache.toAbsolutePath().normalize())));
    }

    @Test
    void arbitraryCacheFileWithoutPf17ManifestIsInconclusive() throws Exception {
        Path save = createSave();
        Pf18SourceSafetyReport report = runner((path, root) -> {
            Files.createDirectories(root);
            Files.writeString(root.resolve("unrelated.txt"), "not PF-1.7");
        }).validate(save, temporaryDirectory.resolve("arbitrary-cache"));

        assertEquals(Pf18SourceSafetyStatus.INCONCLUSIVE, report.status());
        assertFalse(report.accepted());
        assertFalse(report.cacheEvidence().manifestPresent());
        assertFalse(report.cacheEvidence().qualifyingManifest());
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

    @Test
    void escapedArtifactEvidenceCannotBeAccepted() throws Exception {
        Path save = createSave();
        Path cache = temporaryDirectory.resolve("escaped-cache");
        Pf18SourceSafetyReport.Pf18CacheEvidence evidence =
                new Pf18SourceSafetyReport.Pf18CacheEvidence(
                        true, true, false, false, false,
                        List.of(temporaryDirectory.resolve("outside/manifest.properties")));
        Pf18SourceSafetyReport report = new Pf18SourceSafetyReport(
                save, cache, Pf18SourceSafetyRunner.WORKLOAD,
                Pf18SourceSafetyStatus.INCONCLUSIVE,
                java.util.Optional.of(new SaveSafetyResult(
                        SaveSafetyStatus.PASS, List.of())), true, evidence,
                java.util.Optional.empty());

        assertFalse(report.accepted());
        assertFalse(report.cacheEvidence().contained());
    }

    private Pf18SourceSafetyRunner runner(Pf18SourceSafetyRunner.SafetyOperation operation) {
        return new Pf18SourceSafetyRunner(
                new SaveSafetySnapshotter(), new SaveSafetyGate(), operation);
    }

    private static void publishManifest(Path save, Path cacheRoot) throws Exception {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = store.observe(save);
        store.publish(revision);
    }

    private Path createSave() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("source");
        Files.createDirectories(sourceDirectory);
        Path save = Files.createTempFile(sourceDirectory, "save-", ".vcdbs");
        Files.writeString(save, "save");
        return save;
    }
}
