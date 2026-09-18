package cartographer.perf.gui;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public record GuiManualValidationManifest(
        String candidateSha,
        Map<GuiManualCheck, GuiValidationStatus> checks,
        String r4096Outcome,
        String notes
) {
    public static final String FILE_NAME = "manual-validation.properties";

    public GuiManualValidationManifest {
        candidateSha = exactSha(candidateSha);
        Objects.requireNonNull(checks, "checks are required");
        EnumMap<GuiManualCheck, GuiValidationStatus> copy =
                new EnumMap<>(GuiManualCheck.class);
        for (GuiManualCheck check : GuiManualCheck.values()) {
            copy.put(
                    check,
                    Objects.requireNonNull(
                            checks.get(check),
                            "missing manual check " + check
                    )
            );
        }
        checks = Map.copyOf(copy);
        r4096Outcome = Objects.requireNonNullElse(r4096Outcome, "").trim();
        notes = Objects.requireNonNullElse(notes, "").trim();
    }

    public static GuiManualValidationManifest pending(String sha) {
        EnumMap<GuiManualCheck, GuiValidationStatus> checks =
                new EnumMap<>(GuiManualCheck.class);
        for (GuiManualCheck check : GuiManualCheck.values()) {
            checks.put(check, GuiValidationStatus.PENDING);
        }
        return new GuiManualValidationManifest(sha, checks, "", "");
    }

    public static GuiManualValidationManifest load(Path path) {
        Path file = Objects.requireNonNull(path, "path is required")
                .toAbsolutePath()
                .normalize();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read manual validation manifest: " + file,
                    exception
            );
        }

        EnumMap<GuiManualCheck, GuiValidationStatus> checks =
                new EnumMap<>(GuiManualCheck.class);
        for (GuiManualCheck check : GuiManualCheck.values()) {
            String value = required(
                    properties.getProperty(key(check)),
                    key(check)
            );
            checks.put(
                    check,
                    GuiValidationStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT))
            );
        }
        return new GuiManualValidationManifest(
                required(properties.getProperty("candidateSha"), "candidateSha"),
                checks,
                properties.getProperty("r4096Outcome", ""),
                properties.getProperty("notes", "")
        );
    }

    public void write(Path path) {
        Path file = Objects.requireNonNull(path, "path is required")
                .toAbsolutePath()
                .normalize();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            StringBuilder out = new StringBuilder();
            line(out, "candidateSha", candidateSha);
            for (GuiManualCheck check : GuiManualCheck.values()) {
                line(out, key(check), checks.get(check).name());
            }
            line(out, "r4096Outcome", escape(r4096Outcome));
            line(out, "notes", escape(notes));
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write manual validation manifest: " + file,
                    exception
            );
        }
    }

    public boolean allRequiredPassed() {
        return checks.values().stream()
                .allMatch(status -> status == GuiValidationStatus.PASS)
                && !r4096Outcome.isBlank();
    }

    private static String key(GuiManualCheck check) {
        return "check." + check.name();
    }

    private static void line(
            StringBuilder out,
            String key,
            String value
    ) {
        out.append(key).append('=').append(value).append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    static String exactSha(String value) {
        String normalized = required(value, "candidateSha")
                .toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(
                    "candidateSha must be a full 40-character SHA"
            );
        }
        return normalized;
    }
}
