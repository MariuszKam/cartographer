package cartographer.navigation;

import cartographer.cli.CommandException;
import cartographer.model.HomeLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public class HomeStore {
    private final Path configPath;

    public HomeStore(Path configPath) {
        this.configPath = configPath;
    }

    public Optional<HomeLocation> load() {
        if (!Files.exists(configPath)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
            return Optional.of(new HomeLocation(
                    Double.parseDouble(required(properties, "x")),
                    Double.parseDouble(required(properties, "z"))));
        } catch (IOException | NumberFormatException exception) {
            throw new CommandException("Cannot read HOME config at " + configPath + ": " + exception.getMessage(), exception);
        }
    }

    public void save(HomeLocation home) {
        Properties properties = new Properties();
        properties.setProperty("x", Double.toString(home.x()));
        properties.setProperty("z", Double.toString(home.z()));

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "VS Cartographer HOME");
            }
        } catch (IOException exception) {
            throw new CommandException("Cannot write HOME config at " + configPath + ": " + exception.getMessage(), exception);
        }
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new CommandException("HOME config is missing key: " + key);
        }
        return value;
    }
}
