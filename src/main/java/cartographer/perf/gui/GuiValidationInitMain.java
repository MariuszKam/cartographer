package cartographer.perf.gui;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GuiValidationInitMain {
    private GuiValidationInitMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: guiValidationInit <gitSha> <evidenceRoot>"
            );
        }
        String sha = GuiManualValidationManifest.exactSha(args[0]);
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path manifest = root.resolve(
                GuiManualValidationManifest.FILE_NAME
        );
        if (Files.exists(manifest)) {
            throw new IllegalArgumentException(
                    "Manual validation manifest already exists: " + manifest
            );
        }
        GuiManualValidationManifest.pending(sha).write(manifest);
        Files.createDirectories(root.resolve("macro"));
        System.out.println(
                "GUI-P14 evidence initialized: " + root
        );
        System.out.println(
                "Manual checklist: " + manifest
        );
    }
}
