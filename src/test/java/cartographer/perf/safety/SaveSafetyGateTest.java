package cartographer.perf.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSafetyGateTest {
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir
    Path temporaryDirectory;

    @Test
    void unchangedSaveWithNoSidecarsPasses() throws Exception {
        Path save = createSave("abc");

        SaveSafetySnapshot before = snapshotter().capture(save);
        SaveSafetySnapshot after = snapshotter().capture(save);
        SaveSafetyResult result = new SaveSafetyGate().compare(before, after);

        assertEquals(SaveSafetyStatus.PASS, result.status());
        assertTrue(result.violations().isEmpty());
        assertFalse(before.wal().exists());
        assertFalse(before.shm().exists());
        assertFalse(before.journal().exists());
    }

    @Test
    void mainContentChangeOfSameSizeFails() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot before = snapshotter().capture(save);
        Files.writeString(save, "xyz", StandardCharsets.UTF_8);

        SaveSafetyResult result = compare(before, save);

        assertHas(result, SaveSafetyViolationType.SAVE_CONTENT_CHANGED);
    }

    @Test
    void mainSizeChangeFails() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot before = snapshotter().capture(save);
        Files.writeString(save, "larger", StandardCharsets.UTF_8);

        assertHas(compare(before, save), SaveSafetyViolationType.SAVE_SIZE_CHANGED);
    }

    @Test
    void mainModifiedTimestampChangeFails() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot before = snapshotter().capture(save);
        long changedMillis = before.mainSave().lastModified().orElseThrow().toMillis() + 86_400_000L;
        Files.setLastModifiedTime(save, FileTime.fromMillis(changedMillis));

        assertHas(compare(before, save), SaveSafetyViolationType.SAVE_LAST_MODIFIED_CHANGED);
    }

    @Test
    void creationAndRemovalOfEachSidecarFails() throws Exception {
        for (String suffix : List.of("-wal", "-shm", "-journal")) {
            Path save = createSave("abc");
            Path sidecar = sidecar(save, suffix);
            SaveSafetySnapshot before = snapshotter().capture(save);
            Files.writeString(sidecar, "sidecar", StandardCharsets.UTF_8);
            SaveSafetyResult created = compare(before, save);
            assertHas(created, creationType(suffix));

            SaveSafetySnapshot withSidecar = snapshotter().capture(save);
            Files.delete(sidecar);
            SaveSafetyResult removed = compare(withSidecar, save);
            assertHas(removed, removalType(suffix));
        }
    }

    @Test
    void unchangedPreExistingSidecarsPass() throws Exception {
        Path save = createSave("abc");
        Files.writeString(sidecar(save, "-wal"), "wal", StandardCharsets.UTF_8);
        Files.writeString(sidecar(save, "-shm"), "shm", StandardCharsets.UTF_8);
        Files.writeString(sidecar(save, "-journal"), "journal", StandardCharsets.UTF_8);

        SaveSafetySnapshot before = snapshotter().capture(save);
        SaveSafetySnapshot after = snapshotter().capture(save);

        assertEquals(SaveSafetyStatus.PASS, new SaveSafetyGate().compare(before, after).status());
    }

    @Test
    void existingSidecarContentSizeAndTimestampChangesFail() throws Exception {
        for (String suffix : List.of("-wal", "-shm", "-journal")) {
            Path save = createSave("abc");
            Path sidecar = sidecar(save, suffix);
            Files.writeString(sidecar, "original", StandardCharsets.UTF_8);
            SaveSafetySnapshot before = snapshotter().capture(save);
            Files.writeString(sidecar, "altered!!", StandardCharsets.UTF_8);
            SaveSafetyResult content = compare(before, save);
            assertHas(content, contentType(suffix));
            assertHas(content, sizeType(suffix));

            Files.writeString(sidecar, "altered!!", StandardCharsets.UTF_8);
            SaveSafetySnapshot stableContent = snapshotter().capture(save);
            long changedMillis = sidecarSnapshot(stableContent, suffix).lastModified()
                    .orElseThrow().toMillis() + 86_400_000L;
            Files.setLastModifiedTime(sidecar, FileTime.fromMillis(changedMillis));
            assertHas(compare(stableContent, save), modifiedType(suffix));
        }
    }

    @Test
    void differentSavePathsCannotBeCompared() throws Exception {
        SaveSafetySnapshot first = snapshotter().capture(createSave("one"));
        SaveSafetySnapshot second = snapshotter().capture(createSave("two"));

        assertThrows(IllegalArgumentException.class,
                () -> new SaveSafetyGate().compare(first, second));
    }

    @Test
    void missingOrDirectoryMainSaveIsRejected() throws Exception {
        Path missing = temporaryDirectory.resolve("missing.vcdbs");
        assertThrows(SaveSafetyException.class, () -> snapshotter().capture(missing));
        Path directory = temporaryDirectory.resolve("directory.vcdbs");
        Files.createDirectory(directory);
        assertThrows(SaveSafetyException.class, () -> snapshotter().capture(directory));
    }

    @Test
    void hashIsDeterministicLowercaseAndStreamedForMultipleBuffers() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot first = snapshotter().capture(save);
        SaveSafetySnapshot second = snapshotter().capture(save);

        assertEquals(new SaveContentHash(ABC_SHA256), first.mainSave().sha256().orElseThrow());
        assertEquals(first.mainSave().sha256(), second.mainSave().sha256());
        assertEquals(64, first.mainSave().sha256().orElseThrow().sha256Hex().length());
        assertEquals("0123456789abcdef",
                new SaveContentHash("0123456789abcdef".repeat(4)).sha256Hex());

        byte[] multiBuffer = new byte[32 * 1024 + 7];
        Files.write(save, multiBuffer);
        SaveSafetySnapshot streamed = snapshotter().capture(save);
        assertEquals(multiBuffer.length, streamed.mainSave().sizeBytes().orElseThrow());
    }

    @Test
    void zeroLengthSidecarIsPresentAndHasEmptyHash() throws Exception {
        Path save = createSave("abc");
        Files.createFile(sidecar(save, "-wal"));

        SaveFileSnapshot wal = snapshotter().capture(save).wal();

        assertTrue(wal.exists());
        assertTrue(wal.regularFile());
        assertEquals(0L, wal.sizeBytes().orElseThrow());
        assertEquals(new SaveContentHash(EMPTY_SHA256), wal.sha256().orElseThrow());
    }

    @Test
    void multipleViolationsUseMainThenWalThenShmOrder() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot before = snapshotter().capture(save);
        Files.writeString(save, "xyz", StandardCharsets.UTF_8);
        Files.writeString(sidecar(save, "-wal"), "wal", StandardCharsets.UTF_8);
        Files.writeString(sidecar(save, "-shm"), "shm", StandardCharsets.UTF_8);

        List<SaveSafetyViolationType> types = compare(before, save).violations().stream()
                .map(SaveSafetyViolation::type).toList();

        assertEquals(List.of(
                SaveSafetyViolationType.SAVE_CONTENT_CHANGED,
                SaveSafetyViolationType.WAL_CREATED,
                SaveSafetyViolationType.SHM_CREATED
        ), types);
    }

    @Test
    void snapshotsAndViolationsAreImmutableAndAbsentSidecarsHaveNoFakeMetadata() throws Exception {
        Path save = createSave("abc");
        SaveSafetySnapshot snapshot = snapshotter().capture(save);
        SaveSafetyResult result = compare(snapshot, save);

        assertTrue(snapshot.wal().sizeBytes().isEmpty());
        assertTrue(snapshot.wal().lastModified().isEmpty());
        assertTrue(snapshot.wal().sha256().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.violations().clear());
        assertThrows(NullPointerException.class, () ->
                new SaveSafetySnapshotter().capture(null));
    }

    private SaveSafetySnapshotter snapshotter() {
        return new SaveSafetySnapshotter();
    }

    private SaveSafetyResult compare(SaveSafetySnapshot before, Path save) {
        return new SaveSafetyGate().compare(before, snapshotter().capture(save));
    }

    private Path createSave(String contents) throws Exception {
        Path save = Files.createTempFile(temporaryDirectory, "save-", ".vcdbs");
        Files.writeString(save, contents, StandardCharsets.UTF_8);
        return save;
    }

    private static Path sidecar(Path save, String suffix) {
        return save.resolveSibling(save.getFileName() + suffix);
    }

    private static void assertHas(SaveSafetyResult result, SaveSafetyViolationType type) {
        assertTrue(result.violations().stream().anyMatch(violation -> violation.type() == type));
        assertEquals(SaveSafetyStatus.FAIL, result.status());
    }

    private static SaveFileSnapshot sidecarSnapshot(
            SaveSafetySnapshot snapshot,
            String suffix
    ) {
        return switch (suffix) {
            case "-wal" -> snapshot.wal();
            case "-shm" -> snapshot.shm();
            case "-journal" -> snapshot.journal();
            default -> throw new IllegalArgumentException(suffix);
        };
    }

    private static SaveSafetyViolationType creationType(String suffix) {
        return switch (suffix) {
            case "-wal" -> SaveSafetyViolationType.WAL_CREATED;
            case "-shm" -> SaveSafetyViolationType.SHM_CREATED;
            case "-journal" -> SaveSafetyViolationType.JOURNAL_CREATED;
            default -> throw new IllegalArgumentException(suffix);
        };
    }

    private static SaveSafetyViolationType removalType(String suffix) {
        return switch (suffix) {
            case "-wal" -> SaveSafetyViolationType.WAL_REMOVED;
            case "-shm" -> SaveSafetyViolationType.SHM_REMOVED;
            case "-journal" -> SaveSafetyViolationType.JOURNAL_REMOVED;
            default -> throw new IllegalArgumentException(suffix);
        };
    }

    private static SaveSafetyViolationType contentType(String suffix) {
        return switch (suffix) {
            case "-wal" -> SaveSafetyViolationType.WAL_CONTENT_CHANGED;
            case "-shm" -> SaveSafetyViolationType.SHM_CONTENT_CHANGED;
            case "-journal" -> SaveSafetyViolationType.JOURNAL_CONTENT_CHANGED;
            default -> throw new IllegalArgumentException(suffix);
        };
    }

    private static SaveSafetyViolationType sizeType(String suffix) {
        return switch (suffix) {
            case "-wal" -> SaveSafetyViolationType.WAL_SIZE_CHANGED;
            case "-shm" -> SaveSafetyViolationType.SHM_SIZE_CHANGED;
            case "-journal" -> SaveSafetyViolationType.JOURNAL_SIZE_CHANGED;
            default -> throw new IllegalArgumentException(suffix);
        };
    }

    private static SaveSafetyViolationType modifiedType(String suffix) {
        return switch (suffix) {
            case "-wal" -> SaveSafetyViolationType.WAL_LAST_MODIFIED_CHANGED;
            case "-shm" -> SaveSafetyViolationType.SHM_LAST_MODIFIED_CHANGED;
            case "-journal" -> SaveSafetyViolationType.JOURNAL_LAST_MODIFIED_CHANGED;
            default -> throw new IllegalArgumentException(suffix);
        };
    }
}
