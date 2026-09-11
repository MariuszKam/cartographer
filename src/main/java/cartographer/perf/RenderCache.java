package cartographer.perf;

import cartographer.cli.CommandException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenderCache {
    public static final String PARSER_VERSION = "parser-v1";

    private final Path cacheDirectory;

    public RenderCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public CacheKey key(Path savePath) {
        try {
            Path fileName = savePath.getFileName();
            return new CacheKey(
                    fileName == null ? "save" : fileName.toString(),
                    Files.size(savePath),
                    Files.getLastModifiedTime(savePath).toMillis(),
                    PARSER_VERSION);
        } catch (IOException exception) {
            throw new CommandException("Cannot build cache key: " + exception.getMessage(), exception);
        }
    }

    public boolean exists(CacheKey key) {
        return Files.exists(path(key));
    }

    public Path path(CacheKey key) {
        return cacheDirectory.resolve(key.fileName());
    }

    public void write(CacheKey key, String content) {
        try {
            Files.createDirectories(cacheDirectory);
            Files.writeString(path(key), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CommandException("Cannot write cache: " + exception.getMessage(), exception);
        }
    }

    public String read(CacheKey key) {
        try {
            return Files.readString(path(key), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CommandException("Cannot read cache: " + exception.getMessage(), exception);
        }
    }
}
