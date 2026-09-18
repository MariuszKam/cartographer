package cartographer.perf.macro;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Launches one fresh Java process for every PROCESS_COLD sample. */
public final class Pf18JavaProcessLauncher implements Pf18ProcessLauncher {
    private final String javaExecutable;
    private final String classPath;

    public Pf18JavaProcessLauncher() {
        this(System.getProperty("java.home") + java.io.File.separator + "bin"
                        + java.io.File.separator + "java",
                System.getProperty("java.class.path"));
    }

    Pf18JavaProcessLauncher(String javaExecutable, String classPath) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable);
        this.classPath = Objects.requireNonNull(classPath);
    }

    @Override
    public Pf18ProcessResult launch(Path save, Path cacheRoot, String workloadId,
                                    Path evidence) throws Exception {
        long started = System.nanoTime();
        Process process = new ProcessBuilder(
                javaExecutable,
                "--enable-native-access=ALL-UNNAMED",
                "-cp", classPath,
                Pf18MacroChildMain.class.getName(),
                save.toString(), cacheRoot.toString(), workloadId, evidence.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("PF-1.8 child exited " + exit + ": " + output.trim());
        }
        if (!java.nio.file.Files.isRegularFile(evidence)) {
            throw new IOException("PF-1.8 child evidence is missing: " + evidence);
        }
        return new Pf18ProcessResult(Pf18MacroChildMain.readEvidence(evidence),
                System.nanoTime() - started);
    }
}
