package cartographer.perf.snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Pf3RenderSizedValidationMain {
    private Pf3RenderSizedValidationMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: pf3RenderSizedValidation "
                            + "<save> <candidateSha> <outputRoot>"
            );
        }

        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        String sha = Pf28SnapshotValidationReport.fullSha(args[1]);
        Path outputRoot = Path.of(args[2])
                .toAbsolutePath()
                .normalize();

        Pf3RenderSizedValidationReport report =
                new Pf3RenderSizedValidationRunner().run(
                        save,
                        sha,
                        outputRoot
                );
        String rendered = report.render();
        System.out.print(rendered);

        try {
            Files.writeString(
                    outputRoot.resolve("pf3-validation-report.txt"),
                    rendered,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write PF-3 validation report",
                    exception
            );
        }

        if (!report.accepted()) {
            throw new IllegalStateException(
                    "PF-3 render-sized validation did not pass"
            );
        }
    }
}
