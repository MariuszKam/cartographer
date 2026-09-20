package cartographer.perf.snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Pf28SnapshotValidationMain {
    private Pf28SnapshotValidationMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: pf28SnapshotValidation "
                            + "<save> <candidateSha> <outputRoot>"
            );
        }

        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        String sha = Pf28SnapshotValidationReport.fullSha(args[1]);
        Path outputRoot = Path.of(args[2]).toAbsolutePath().normalize();

        Pf28SnapshotValidationReport report =
                new Pf28SnapshotValidationRunner().run(
                        save,
                        sha,
                        outputRoot
                );
        String rendered = report.render();
        System.out.print(rendered);

        try {
            Files.createDirectories(outputRoot);
            Files.writeString(
                    outputRoot.resolve("pf28-validation-report.txt"),
                    rendered,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write PF-2.8 validation report",
                    exception
            );
        }

        if (!report.accepted()) {
            throw new IllegalStateException(
                    "PF-2.8 validation did not pass"
            );
        }
    }
}
