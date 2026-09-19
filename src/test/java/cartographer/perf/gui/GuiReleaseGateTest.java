package cartographer.perf.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiReleaseGateTest {
    private static final String SHA =
            "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsCompleteEvidenceAndAllowsRecordedInvalidR4096Attempt()
            throws Exception {
        writeCompleteBaseEvidence();
        macro("MAP_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R2048", "cache_warm", "FACTUAL", "PASS", true);
        macro("ROCK_UPPER_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R4096", "jvm_warm", "INVALID", "PASS", false);
        macro("ROCK_UPPER_R4096", "jvm_warm", "INVALID", "PASS", false);

        GuiReleaseGateReport report =
                new GuiReleaseGate().evaluate(SHA, temporaryDirectory);

        assertTrue(report.accepted(), report.render());
    }

    @Test
    void acceptsTerminalStretchAttemptWhenR4096ReportWasNotProduced()
            throws Exception {
        writeCompleteBaseEvidence();
        macro("MAP_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R2048", "cache_warm", "FACTUAL", "PASS", true);
        macro("ROCK_UPPER_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        stretchAttempt("MAP_R4096", "jvm_warm", "OUT_OF_MEMORY");
        stretchAttempt("ROCK_UPPER_R4096", "jvm_warm", "FAILED");

        GuiReleaseGateReport report =
                new GuiReleaseGate().evaluate(SHA, temporaryDirectory);

        assertTrue(report.accepted(), report.render());
    }

    @Test
    void rejectsMissingMandatoryFactualR2048Evidence() throws Exception {
        writeCompleteBaseEvidence();
        macro("MAP_R2048", "jvm_warm", "INVALID", "PASS", false);
        macro("MAP_R2048", "cache_warm", "FACTUAL", "PASS", true);
        macro("ROCK_UPPER_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R4096", "jvm_warm", "INVALID", "PASS", false);
        macro("ROCK_UPPER_R4096", "jvm_warm", "INVALID", "PASS", false);

        GuiReleaseGateReport report =
                new GuiReleaseGate().evaluate(SHA, temporaryDirectory);

        assertFalse(report.accepted());
        assertTrue(report.failures().stream().anyMatch(
                value -> value.contains("MAP_R2048/jvm_warm")
        ));
    }

    @Test
    void rejectsCandidateShaMismatch() throws Exception {
        writeCompleteBaseEvidence();
        macro("MAP_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R2048", "cache_warm", "FACTUAL", "PASS", true);
        macro("ROCK_UPPER_R2048", "jvm_warm", "FACTUAL", "PASS", false);
        macro("MAP_R4096", "jvm_warm", "INVALID", "PASS", false);
        macro("ROCK_UPPER_R4096", "jvm_warm", "INVALID", "PASS", false);

        GuiReleaseGateReport report = new GuiReleaseGate().evaluate(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                temporaryDirectory
        );

        assertFalse(report.accepted());
        assertTrue(report.failures().stream().anyMatch(
                value -> value.contains("SHA mismatch")
                        || value.contains("candidate SHA mismatch")
        ));
    }

    private void writeCompleteBaseEvidence() throws Exception {
        Files.writeString(
                temporaryDirectory.resolve(GuiReleaseGate.PREFLIGHT_FILE),
                "candidateSha=" + SHA + "\nunitTests=PASS\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve(GuiReleaseGate.SOURCE_SAFETY_FILE),
                "candidateSha=" + SHA + "\n"
                        + "realSaveStatus=PASS\n"
                        + "pf18Status=PASS\n"
                        + "operationCompleted=true\n"
                        + "cacheContained=true\n"
                        + "qualifyingManifest=true\n",
                StandardCharsets.UTF_8
        );

        EnumMap<GuiManualCheck, GuiValidationStatus> checks =
                new EnumMap<>(GuiManualCheck.class);
        for (GuiManualCheck check : GuiManualCheck.values()) {
            checks.put(check, GuiValidationStatus.PASS);
        }
        new GuiManualValidationManifest(
                SHA,
                checks,
                "MAP R4096 completed; ROCK R4096 OOM recorded",
                "manual smoke complete"
        ).write(
                temporaryDirectory.resolve(
                        GuiManualValidationManifest.FILE_NAME
                )
        );
    }

    private void macro(
            String workload,
            String mode,
            String verdict,
            String safety,
            boolean cacheHit
    ) throws Exception {
        Path report = temporaryDirectory.resolve("macro")
                .resolve(workload + "-" + mode)
                .resolve("macro-report.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                "Git SHA: " + SHA + "\n"
                        + "Workload: " + workload + "\n"
                        + "Source safety: " + safety + "\n"
                        + "Cache HIT verified: " + cacheHit + "\n"
                        + "Evidence verdict: " + verdict + "\n",
                StandardCharsets.UTF_8
        );
    }
    private void stretchAttempt(
            String workload,
            String mode,
            String outcome
    ) throws Exception {
        Path file = temporaryDirectory.resolve("macro")
                .resolve("attempts")
                .resolve(workload + "-" + mode + ".properties");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "candidateSha=" + SHA + "\n"
                        + "workload=" + workload + "\n"
                        + "mode=" + mode.toUpperCase(java.util.Locale.ROOT) + "\n"
                        + "outcome=" + outcome + "\n",
                StandardCharsets.UTF_8
        );
    }

}
