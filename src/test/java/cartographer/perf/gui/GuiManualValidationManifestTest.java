package cartographer.perf.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GuiManualValidationManifestTest {
    private static final String SHA =
            "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path temporaryDirectory;

    @Test
    void pendingTemplateRoundTripsDeterministically() {
        Path file = temporaryDirectory.resolve(
                GuiManualValidationManifest.FILE_NAME
        );
        GuiManualValidationManifest.pending(SHA).write(file);

        GuiManualValidationManifest loaded =
                GuiManualValidationManifest.load(file);

        assertEquals(SHA, loaded.candidateSha());
        assertFalse(loaded.allRequiredPassed());
        for (GuiManualCheck check : GuiManualCheck.values()) {
            assertEquals(
                    GuiValidationStatus.PENDING,
                    loaded.checks().get(check)
            );
        }
    }
}
