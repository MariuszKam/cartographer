package cartographer.marker;

import cartographer.cli.CommandException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MarkerStore {
    private final Path path;

    public MarkerStore(Path path) {
        this.path = path;
    }

    public List<UserMarker> load() {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<UserMarker> markers = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", 3);
                if (parts.length == 3) {
                    markers.add(new UserMarker(unescape(parts[2]), Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
                }
            }
            return List.copyOf(markers);
        } catch (IOException | NumberFormatException exception) {
            throw new CommandException("Cannot read markers: " + exception.getMessage(), exception);
        }
    }

    public void add(UserMarker marker) {
        try {
            Files.createDirectories(path.getParent());
            String line = marker.x() + "," + marker.z() + "," + escape(marker.name()) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8, Files.exists(path)
                    ? java.nio.file.StandardOpenOption.APPEND
                    : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException exception) {
            throw new CommandException("Cannot write marker: " + exception.getMessage(), exception);
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }

    private String unescape(String value) {
        return value.replace("\\,", ",").replace("\\\\", "\\");
    }
}
