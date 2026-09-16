package cartographer.perf.safety;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Opt-in command entry point for real-save read-only safety validation. */
public final class RealSaveValidationMain {
    private RealSaveValidationMain() {
    }

    public static void main(String[] args) {
        Path save = parseSave(args);
        try {
            SaveSafetyResult result = new RealSaveValidationRunner().validate(save);
            printResult(save, result);
            if (result.status() == SaveSafetyStatus.FAIL) {
                throw new IllegalStateException("Protected save state changed");
            }
        } catch (RealSaveValidationException failure) {
            failure.safetyResult().ifPresent(result -> printResult(save, result));
            throw failure;
        }
    }

    private static Path parseSave(String[] args) {
        if (args == null || args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: realSaveValidation <path-to-world.vcdbs>");
        }
        try {
            Path save = Path.of(args[0].trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(save)) {
                throw new IllegalArgumentException(
                        "Save path must be an existing regular file: " + save);
            }
            return save;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid save path: " + args[0], exception);
        }
    }

    private static void printResult(Path save, SaveSafetyResult result) {
        System.out.println("Real-save validation: " + result.status().name());
        System.out.println("Save: " + save);
        if (result.violations().isEmpty()) {
            System.out.println("Violations: none");
        } else {
            System.out.println("Violations:");
            result.violations().forEach(violation -> System.out.println(
                    "- " + violation.type().name() + ": " + violation.path()));
        }
    }
}
