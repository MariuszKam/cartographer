package cartographer.update;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

public final class UpdatePreferencesStore {
    private final Path path;

    public UpdatePreferencesStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public UpdatePreferences load() {
        if (!Files.isRegularFile(path)) {
            return UpdatePreferences.defaults();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
        } catch (IOException exception) {
            return UpdatePreferences.defaults();
        }

        boolean autoCheck = parseBoolean(
                properties.getProperty("autoCheck")
        );
        Optional<Instant> lastSuccessfulCheck = parseInstant(
                properties.getProperty("lastSuccessfulCheck")
        );
        return new UpdatePreferences(autoCheck, lastSuccessfulCheck);
    }

    public void save(UpdatePreferences preferences) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties properties = new Properties();
        properties.setProperty(
                "autoCheck",
                Boolean.toString(preferences.autoCheck())
        );
        preferences.lastSuccessfulCheck().ifPresent(instant ->
                properties.setProperty(
                        "lastSuccessfulCheck",
                        instant.toString()
                )
        );

        Path temporary = path.resolveSibling(
                path.getFileName() + ".tmp"
        );
        try (Writer writer = Files.newBufferedWriter(
                temporary,
                StandardCharsets.UTF_8
        )) {
            properties.store(writer, "VS Cartographer update preferences");
        }

        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException atomicMoveFailure) {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        return true;
    }

    private Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.trim()));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
