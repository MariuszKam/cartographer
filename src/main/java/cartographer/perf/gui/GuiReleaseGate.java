package cartographer.perf.gui;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class GuiReleaseGate {
    public static final String PREFLIGHT_FILE = "preflight.properties";
    public static final String SOURCE_SAFETY_FILE = "source-safety.properties";
    public static final String REPORT_FILE = "release-gate-report.txt";

    public GuiReleaseGateReport evaluate(
            String candidateSha,
            Path evidenceRoot
    ) {
        String sha = GuiManualValidationManifest.exactSha(candidateSha);
        Path root = Objects.requireNonNull(
                evidenceRoot,
                "evidenceRoot is required"
        ).toAbsolutePath().normalize();

        List<String> passed = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        checkPreflight(sha, root.resolve(PREFLIGHT_FILE), passed, failures);
        checkSourceSafety(
                sha,
                root.resolve(SOURCE_SAFETY_FILE),
                passed,
                failures
        );
        checkManual(
                sha,
                root.resolve(GuiManualValidationManifest.FILE_NAME),
                passed,
                failures
        );

        checkMacro(
                sha,
                root,
                "MAP_R2048",
                "jvm_warm",
                true,
                false,
                passed,
                failures
        );
        checkMacro(
                sha,
                root,
                "MAP_R2048",
                "cache_warm",
                true,
                true,
                passed,
                failures
        );
        checkMacro(
                sha,
                root,
                "ROCK_UPPER_R2048",
                "jvm_warm",
                true,
                false,
                passed,
                failures
        );

        // R4096 is stretch/headroom evidence. The attempt and factual metadata
        // are required, but an OOM/INVALID outcome is still valid evidence.
        checkMacro(
                sha,
                root,
                "MAP_R4096",
                "jvm_warm",
                false,
                false,
                passed,
                failures
        );
        checkMacro(
                sha,
                root,
                "ROCK_UPPER_R4096",
                "jvm_warm",
                false,
                false,
                passed,
                failures
        );

        return new GuiReleaseGateReport(sha, passed, failures);
    }

    private void checkPreflight(
            String sha,
            Path file,
            List<String> passed,
            List<String> failures
    ) {
        Properties properties = load(file, "preflight", failures);
        if (properties == null) return;
        if (!matchesSha(sha, properties, file, failures)) return;
        if (!"PASS".equals(properties.getProperty("unitTests"))) {
            failures.add("preflight unitTests must be PASS: " + file);
            return;
        }
        passed.add("automated preflight / unit tests");
    }

    private void checkSourceSafety(
            String sha,
            Path file,
            List<String> passed,
            List<String> failures
    ) {
        Properties properties = load(file, "source-safety", failures);
        if (properties == null) return;
        if (!matchesSha(sha, properties, file, failures)) return;

        requireProperty(properties, "realSaveStatus", "PASS", file, failures);
        requireProperty(properties, "pf18Status", "PASS", file, failures);
        requireProperty(properties, "operationCompleted", "true", file, failures);
        requireProperty(properties, "cacheContained", "true", file, failures);
        requireProperty(properties, "qualifyingManifest", "true", file, failures);

        if (failures.stream().noneMatch(value -> value.contains(file.toString()))) {
            passed.add("real-save + PF-1.8 source safety");
        }
    }

    private void checkManual(
            String sha,
            Path file,
            List<String> passed,
            List<String> failures
    ) {
        if (!Files.isRegularFile(file)) {
            failures.add("manual validation manifest missing: " + file);
            return;
        }
        try {
            GuiManualValidationManifest manifest =
                    GuiManualValidationManifest.load(file);
            if (!manifest.candidateSha().equals(sha)) {
                failures.add(
                        "manual validation SHA mismatch: "
                                + manifest.candidateSha()
                                + " != " + sha
                );
                return;
            }
            for (GuiManualCheck check : GuiManualCheck.values()) {
                GuiValidationStatus status = manifest.checks().get(check);
                if (status != GuiValidationStatus.PASS) {
                    failures.add(
                            "manual check not PASS: "
                                    + check + "=" + status
                    );
                }
            }
            if (manifest.r4096Outcome().isBlank()) {
                failures.add("manual R4096 outcome is not recorded");
            }
            if (manifest.allRequiredPassed()) {
                passed.add("manual Workstation / P13 / cancellation acceptance");
            }
        } catch (RuntimeException failure) {
            failures.add(
                    "manual validation manifest invalid: "
                            + file + " — " + failure.getMessage()
            );
        }
    }

    private void checkMacro(
            String sha,
            Path root,
            String workload,
            String mode,
            boolean requireFactual,
            boolean requireCacheHit,
            List<String> passed,
            List<String> failures
    ) {
        Path report = root.resolve("macro")
                .resolve(workload + "-" + mode)
                .resolve("macro-report.txt");
        if (!Files.isRegularFile(report)) {
            if (!requireFactual
                    && checkStretchAttempt(
                    sha,
                    root,
                    workload,
                    mode,
                    passed,
                    failures
            )) {
                failures.add(
                        "stretch macro attempt evidence missing: " + report
                );
            } else if (requireFactual) {
                failures.add("macro evidence missing: " + report);
            }
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            failures.add("cannot read macro evidence: " + report);
            return;
        }

        String reportSha = value(lines, "Git SHA:");
        String reportWorkload = value(lines, "Workload:");
        String verdict = value(lines, "Evidence verdict:");
        String safety = value(lines, "Source safety:");
        String cacheHit = value(lines, "Cache HIT verified:");

        boolean valid = true;
        if (!sha.equals(reportSha)) {
            failures.add(
                    "macro SHA mismatch for " + workload
                            + ": " + reportSha + " != " + sha
            );
            valid = false;
        }
        if (!workload.equals(reportWorkload)) {
            failures.add(
                    "macro workload mismatch in " + report
                            + ": " + reportWorkload
            );
            valid = false;
        }
        if (verdict == null) {
            failures.add("macro verdict missing: " + report);
            valid = false;
        }
        if (safety == null) {
            failures.add("macro source-safety line missing: " + report);
            valid = false;
        }
        if (requireFactual && !"FACTUAL".equals(verdict)) {
            failures.add(
                    "macro evidence must be FACTUAL for "
                            + workload + "/" + mode
                            + ": " + verdict
            );
            valid = false;
        }
        if (requireFactual && !"PASS".equals(safety)) {
            failures.add(
                    "macro source safety must be PASS for "
                            + workload + "/" + mode
                            + ": " + safety
            );
            valid = false;
        }
        if (requireCacheHit && !"true".equalsIgnoreCase(cacheHit)) {
            failures.add(
                    "cache-warm macro must verify HIT for "
                            + workload + ": " + cacheHit
            );
            valid = false;
        }
        if (valid) {
            passed.add(
                    workload + "/" + mode
                            + (requireFactual
                            ? " factual macro evidence"
                            : " stretch attempt evidence (" + verdict + ")")
            );
        }
    }

    private boolean checkStretchAttempt(
            String sha,
            Path root,
            String workload,
            String mode,
            List<String> passed,
            List<String> failures
    ) {
        Path file = root.resolve("macro")
                .resolve("attempts")
                .resolve(workload + "-" + mode + ".properties");
        Properties properties = load(
                file,
                "stretch-attempt",
                failures
        );
        if (properties == null) {
            return false;
        }
        if (!matchesSha(sha, properties, file, failures)) {
            return false;
        }
        String actualWorkload = properties.getProperty("workload");
        String outcome = properties.getProperty("outcome");
        if (!workload.equals(actualWorkload)) {
            failures.add(
                    "stretch workload mismatch in " + file
                            + ": " + actualWorkload
            );
            return false;
        }
        if (outcome == null
                || outcome.isBlank()
                || "STARTED".equals(outcome)) {
            failures.add(
                    "stretch attempt has no terminal outcome: " + file
            );
            return false;
        }
        passed.add(
                workload + "/" + mode
                        + " stretch attempt evidence (" + outcome + ")"
        );
        return true;
    }

    private Properties load(
            Path file,
            String label,
            List<String> failures
    ) {
        if (!Files.isRegularFile(file)) {
            failures.add(label + " evidence missing: " + file);
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            failures.add("cannot read " + label + " evidence: " + file);
            return null;
        }
    }

    private boolean matchesSha(
            String expected,
            Properties properties,
            Path file,
            List<String> failures
    ) {
        String actual = properties.getProperty("candidateSha");
        if (!expected.equals(actual)) {
            failures.add(
                    "candidate SHA mismatch in " + file
                            + ": " + actual + " != " + expected
            );
            return false;
        }
        return true;
    }

    private void requireProperty(
            Properties properties,
            String key,
            String expected,
            Path file,
            List<String> failures
    ) {
        String actual = properties.getProperty(key);
        if (!expected.equalsIgnoreCase(actual == null ? "" : actual)) {
            failures.add(
                    key + " must be " + expected
                            + " in " + file + " (was " + actual + ")"
            );
        }
    }

    private String value(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse(null);
    }
}
