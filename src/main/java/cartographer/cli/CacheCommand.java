package cartographer.cli;

import cartographer.perf.CacheKey;
import cartographer.perf.RenderCache;
import cartographer.save.SaveIndex;
import cartographer.save.SaveIndexReader;
import cartographer.save.TableIndex;

import java.io.PrintStream;
import java.nio.file.Path;

public class CacheCommand implements Command {
    private final PrintStream out;
    private final RenderCache cache;
    private final SaveIndexReader indexReader;
    private final String subcommand;

    public CacheCommand(PrintStream out, RenderCache cache, SaveIndexReader indexReader, String subcommand) {
        this.out = out;
        this.cache = cache;
        this.indexReader = indexReader;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: cache " + subcommand + " <save.vcdbs>");
        }
        Path savePath = Path.of(args[0]);
        switch (subcommand) {
            case "warm" -> warm(savePath);
            case "status" -> status(savePath);
            default -> throw new CommandException("Unknown cache subcommand: " + subcommand);
        }

        return 0;
    }

    private void warm(Path savePath) {
        ProgressReporter progress = new ProgressReporter(out);
        SaveIndex index = indexReader.read(savePath, progress);
        CacheKey key = cache.key(savePath);
        cache.write(key, serialize(index));
        out.println("CACHE WARMED");
        out.println("Key: " + key.fileName());
        out.println("Path: " + cache.path(key));
    }

    private void status(Path savePath) {
        CacheKey key = cache.key(savePath);
        out.println("CACHE");
        out.println("Key: " + key.fileName());
        out.println("Exists: " + cache.exists(key));
        if (cache.exists(key)) {
            out.println(cache.read(key));
        }
    }

    private String serialize(SaveIndex index) {
        StringBuilder builder = new StringBuilder();
        for (TableIndex table : index.tables()) {
            builder.append(table.tableName())
                    .append(',')
                    .append(table.rows())
                    .append(',')
                    .append(table.minPosition())
                    .append(',')
                    .append(table.maxPosition())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }
}
