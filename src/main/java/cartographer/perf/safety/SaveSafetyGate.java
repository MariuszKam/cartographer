package cartographer.perf.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compares two snapshots; it does not execute or wrap the operation between them. */
public final class SaveSafetyGate {
    public SaveSafetyResult compare(
            SaveSafetySnapshot before,
            SaveSafetySnapshot after
    ) {
        Objects.requireNonNull(before, "before is required");
        Objects.requireNonNull(after, "after is required");
        if (!before.savePath().equals(after.savePath())) {
            throw new IllegalArgumentException("snapshots refer to different save paths");
        }

        List<SaveSafetyViolation> violations = new ArrayList<>();
        compareMain(before.mainSave(), after.mainSave(), violations);
        compareSidecar(before.wal(), after.wal(), SaveSafetyViolationType.WAL_CREATED,
                SaveSafetyViolationType.WAL_REMOVED, SaveSafetyViolationType.WAL_FILE_TYPE_CHANGED,
                SaveSafetyViolationType.WAL_SIZE_CHANGED, SaveSafetyViolationType.WAL_LAST_MODIFIED_CHANGED,
                SaveSafetyViolationType.WAL_CONTENT_CHANGED, violations);
        compareSidecar(before.shm(), after.shm(), SaveSafetyViolationType.SHM_CREATED,
                SaveSafetyViolationType.SHM_REMOVED, SaveSafetyViolationType.SHM_FILE_TYPE_CHANGED,
                SaveSafetyViolationType.SHM_SIZE_CHANGED, SaveSafetyViolationType.SHM_LAST_MODIFIED_CHANGED,
                SaveSafetyViolationType.SHM_CONTENT_CHANGED, violations);
        compareSidecar(before.journal(), after.journal(), SaveSafetyViolationType.JOURNAL_CREATED,
                SaveSafetyViolationType.JOURNAL_REMOVED, SaveSafetyViolationType.JOURNAL_FILE_TYPE_CHANGED,
                SaveSafetyViolationType.JOURNAL_SIZE_CHANGED, SaveSafetyViolationType.JOURNAL_LAST_MODIFIED_CHANGED,
                SaveSafetyViolationType.JOURNAL_CONTENT_CHANGED, violations);
        return new SaveSafetyResult(
                violations.isEmpty() ? SaveSafetyStatus.PASS : SaveSafetyStatus.FAIL,
                violations
        );
    }

    private static void compareMain(
            SaveFileSnapshot before,
            SaveFileSnapshot after,
            List<SaveSafetyViolation> violations
    ) {
        if (before.exists() != after.exists()) {
            violations.add(new SaveSafetyViolation(
                    SaveSafetyViolationType.SAVE_EXISTENCE_CHANGED, before.path()
            ));
        }
        if (before.regularFile() != after.regularFile()) {
            violations.add(new SaveSafetyViolation(
                    SaveSafetyViolationType.SAVE_FILE_TYPE_CHANGED, before.path()
            ));
        }
        if (different(before.sizeBytes(), after.sizeBytes())) {
            violations.add(new SaveSafetyViolation(
                    SaveSafetyViolationType.SAVE_SIZE_CHANGED, before.path()
            ));
        }
        if (different(before.lastModified(), after.lastModified())) {
            violations.add(new SaveSafetyViolation(
                    SaveSafetyViolationType.SAVE_LAST_MODIFIED_CHANGED, before.path()
            ));
        }
        if (different(before.sha256(), after.sha256())) {
            violations.add(new SaveSafetyViolation(
                    SaveSafetyViolationType.SAVE_CONTENT_CHANGED, before.path()
            ));
        }
    }

    private static void compareSidecar(
            SaveFileSnapshot before,
            SaveFileSnapshot after,
            SaveSafetyViolationType created,
            SaveSafetyViolationType removed,
            SaveSafetyViolationType typeChanged,
            SaveSafetyViolationType sizeChanged,
            SaveSafetyViolationType modifiedChanged,
            SaveSafetyViolationType contentChanged,
            List<SaveSafetyViolation> violations
    ) {
        if (before.exists() != after.exists()) {
            violations.add(new SaveSafetyViolation(
                    after.exists() ? created : removed, before.path()
            ));
            return;
        }
        if (!before.exists()) {
            return;
        }
        if (before.regularFile() != after.regularFile()) {
            violations.add(new SaveSafetyViolation(typeChanged, before.path()));
        }
        if (different(before.sizeBytes(), after.sizeBytes())) {
            violations.add(new SaveSafetyViolation(sizeChanged, before.path()));
        }
        if (different(before.lastModified(), after.lastModified())) {
            violations.add(new SaveSafetyViolation(modifiedChanged, before.path()));
        }
        if (different(before.sha256(), after.sha256())) {
            violations.add(new SaveSafetyViolation(contentChanged, before.path()));
        }
    }

    private static boolean different(Object left, Object right) {
        return !Objects.equals(left, right);
    }
}
