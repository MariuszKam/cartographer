package cartographer.perf.gui;

import cartographer.perf.macro.MacroBaselineEnvironment;
import cartographer.perf.macro.Pf18JavaProcessLauncher;
import cartographer.perf.macro.Pf18MacroReport;
import cartographer.perf.macro.Pf18MacroRunner;
import cartographer.perf.macro.Pf18ProductionOperationFactory;
import cartographer.perf.metrics.ExecutionMode;

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
        try {
            Pf18MacroReport report = runner.run(
                    save,
                    cacheRoot,
                    workload,
                    sha,
                    mode,
                    macroRoot
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
            System.out.println(
                    "  OUT_OF_MEMORY: " + failure
            );
            if (factualRequired) {
                mandatoryFailures.add(
                        workload + "/" + mode + " OUT_OF_MEMORY"
                );
            }
        } catch (RuntimeException failure) {
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
}
