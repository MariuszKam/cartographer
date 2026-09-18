package cartographer.perf.gui;

import cartographer.perf.macro.MacroBaselineEnvironment;
import cartographer.perf.macro.Pf18JavaProcessLauncher;
import cartographer.perf.macro.Pf18MacroReport;
import cartographer.perf.macro.Pf18MacroRunner;
import cartographer.perf.macro.Pf18ProductionOperationFactory;
import cartographer.perf.metrics.ExecutionMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GuiMacroEvidenceMain {
    private GuiMacroEvidenceMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: guiMacroEvidence "
                            + "<save> <cacheRoot> <gitSha> <evidenceRoot>"
            );
        }

        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        Path cacheRoot = Path.of(args[1]).toAbsolutePath().normalize();
        String sha = GuiManualValidationManifest.exactSha(args[2]);
        Path root = Path.of(args[3]).toAbsolutePath().normalize();
        Path macroRoot = root.resolve("macro");
        Path stateRoot = root.resolve("macro-state");

        if (!Files.isRegularFile(save)) {
            throw new IllegalArgumentException(
                    "save must be an existing regular file: " + save
            );
        }
        requireExternalEvidenceRoot(save, root);

        Pf18MacroRunner runner = new Pf18MacroRunner(
                new Pf18ProductionOperationFactory(stateRoot),
                new Pf18JavaProcessLauncher(),
                MacroBaselineEnvironment.capture()
        );

        List<String> mandatoryFailures = new ArrayList<>();
        run(
                runner,
                save,
                cacheRoot,
                sha,
                macroRoot,
                "MAP_R2048",
                ExecutionMode.JVM_WARM,
                true,
                mandatoryFailures
        );
        run(
                runner,
                save,
                cacheRoot,
                sha,
                macroRoot,
                "MAP_R2048",
                ExecutionMode.CACHE_WARM,
                true,
                mandatoryFailures
        );
        run(
                runner,
                save,
                cacheRoot,
                sha,
                macroRoot,
                "ROCK_UPPER_R2048",
                ExecutionMode.JVM_WARM,
                true,
                mandatoryFailures
        );

        run(
                runner,
                save,
                cacheRoot,
                sha,
                macroRoot,
                "MAP_R4096",
                ExecutionMode.JVM_WARM,
                false,
                mandatoryFailures
        );
        run(
                runner,
                save,
                cacheRoot,
                sha,
                macroRoot,
                "ROCK_UPPER_R4096",
                ExecutionMode.JVM_WARM,
                false,
                mandatoryFailures
        );

        if (!mandatoryFailures.isEmpty()) {
            throw new IllegalStateException(
                    "GUI-P14 mandatory R2048 macro evidence failed: "
                            + String.join("; ", mandatoryFailures)
            );
        }
    }

    private static void run(
            Pf18MacroRunner runner,
            Path save,
            Path cacheRoot,
            String sha,
            Path macroRoot,
            String workload,
            ExecutionMode mode,
            boolean factualRequired,
            List<String> mandatoryFailures
    ) {
        System.out.println(
                "GUI-P14 macro: " + workload + " / " + mode
        );
        writeAttempt(
                macroRoot,
                sha,
                workload,
                mode,
                "STARTED",
                ""
        );
        try {
            Pf18MacroReport report = runner.run(
                    save,
                    cacheRoot,
                    workload,
                    sha,
                    mode,
                    macroRoot
            );
            String outcome = report.evidenceIsValid()
                    ? "FACTUAL"
                    : "INVALID";
            writeAttempt(
                    macroRoot,
                    sha,
                    workload,
                    mode,
                    outcome,
                    report.outputPath().toString()
            );
            System.out.println(
                    "  report=" + report.outputPath()
                            + ", factual=" + report.evidenceIsValid()
            );
            if (factualRequired && !report.evidenceIsValid()) {
                mandatoryFailures.add(
                        workload + "/" + mode + " INVALID"
                );
            }
        } catch (OutOfMemoryError failure) {
            writeAttempt(
                    macroRoot,
                    sha,
                    workload,
                    mode,
                    "OUT_OF_MEMORY",
                    failure.toString()
            );
            System.out.println(
                    "  OUT_OF_MEMORY: " + failure
            );
            if (factualRequired) {
                mandatoryFailures.add(
                        workload + "/" + mode + " OUT_OF_MEMORY"
                );
            }
        } catch (RuntimeException failure) {
            writeAttempt(
                    macroRoot,
                    sha,
                    workload,
                    mode,
                    "FAILED",
                    failure.toString()
            );
            System.out.println(
                    "  FAILED: " + failure
            );
            if (factualRequired) {
                mandatoryFailures.add(
                        workload + "/" + mode + " " + failure
                );
            }
        }
    }
    private static void writeAttempt(
            Path macroRoot,
            String sha,
            String workload,
            ExecutionMode mode,
            String outcome,
            String detail
    ) {
        Path file = macroRoot.resolve("attempts")
                .resolve(workload + "-" + mode.name().toLowerCase(
                        java.util.Locale.ROOT
                ) + ".properties");
        try {
            Files.createDirectories(file.getParent());
            String text = "candidateSha=" + sha + "\n"
                    + "workload=" + workload + "\n"
                    + "mode=" + mode + "\n"
                    + "outcome=" + outcome + "\n"
                    + "detail=" + escape(detail) + "\n";
            Files.writeString(
                    file,
                    text,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write GUI macro attempt evidence: " + file,
                    exception
            );
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static void requireExternalEvidenceRoot(
            Path save,
            Path root
    ) {
        Path sourceDirectory = java.util.Objects.requireNonNull(
                save.getParent(),
                "save parent is required"
        ).toAbsolutePath().normalize();
        if (root.equals(save)
                || root.startsWith(sourceDirectory)
                || sourceDirectory.startsWith(root)) {
            throw new IllegalArgumentException(
                    "evidenceRoot must be outside the source save directory: "
                            + root
            );
        }
    }

}
