package cartographer.perf;

import cartographer.cli.CommandException;
import cartographer.save.SaveIndex;
import cartographer.save.TableIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class IncrementalRenderIndex {
    private final Path directory;

    public IncrementalRenderIndex(Path directory) {
        this.directory = directory;
    }

    public boolean exists(CacheKey key) {
        return Files.exists(path(key));
    }

    public Path path(CacheKey key) {
        return directory.resolve(key.fileName() + ".incremental");
    }

    public IncrementalState from(SaveIndex index, CacheKey key) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (TableIndex table : index.tables()) {
            fingerprints.put(table.tableName(), fingerprint(table));
        }
        return new IncrementalState(key.fileName(), Map.copyOf(fingerprints));
    }

    public void write(CacheKey key, IncrementalState state) {
        try {
            Files.createDirectories(directory);
            StringBuilder builder = new StringBuilder();
            builder.append("cacheKey=").append(state.cacheKey()).append(System.lineSeparator());
            for (Map.Entry<String, String> entry : state.tableFingerprints().entrySet()) {
                builder.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
            }
            Files.writeString(path(key), builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CommandException("Cannot write incremental index: " + exception.getMessage(), exception);
        }
    }

    public IncrementalState read(CacheKey key) {
        try {
            Map<String, String> fingerprints = new LinkedHashMap<>();
            String cacheKey = key.fileName();
            for (String line : Files.readAllLines(path(key), StandardCharsets.UTF_8)) {
                if (line.isBlank() || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if ("cacheKey".equals(parts[0])) {
                    cacheKey = parts[1];
                } else {
                    fingerprints.put(parts[0], parts[1]);
                }
            }
            return new IncrementalState(cacheKey, Map.copyOf(fingerprints));
        } catch (IOException exception) {
            throw new CommandException("Cannot read incremental index: " + exception.getMessage(), exception);
        }
    }

    public Map<String, String> changes(IncrementalState previous, IncrementalState current) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : current.tableFingerprints().entrySet()) {
            String oldValue = previous.tableFingerprints().get(entry.getKey());
            if (!entry.getValue().equals(oldValue)) {
                changes.put(entry.getKey(), oldValue == null ? "new" : "changed");
            }
        }
        return changes;
    }

    private String fingerprint(TableIndex table) {
        return table.rows() + ":" + table.minPosition() + ":" + table.maxPosition();
    }
}
