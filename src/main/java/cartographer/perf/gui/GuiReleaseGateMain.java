package cartographer.perf.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GuiReleaseGateMain {
    private GuiReleaseGateMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: guiReleaseGate <gitSha> <evidenceRoot>"
            );
        }
        String sha = GuiManualValidationManifest.exactSha(args[0]);
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        GuiReleaseGateReport report =
                new GuiReleaseGate().evaluate(sha, root);
        String rendered = report.render();
        System.out.print(rendered);
        try {
            Files.createDirectories(root);
            Files.writeString(
                    root.resolve(GuiReleaseGate.REPORT_FILE),
                    rendered,
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write GUI release-gate report",
                    exception
            );
        }
        if (!report.accepted()) {
            throw new IllegalStateException(
                    "GUI-P14 release gate did not pass"
            );
        }
    }
}
