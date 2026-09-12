package cartographer.cli;

import cartographer.perf.CacheKey;
import cartographer.perf.IncrementalRenderIndex;
import cartographer.perf.IncrementalState;
import cartographer.perf.RenderCache;
import cartographer.save.SaveIndex;
import cartographer.save.SaveIndexReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

public class IncrementalCommand implements Command {
    private final PrintStream out;
    private final RenderCache cache;
    private final IncrementalRenderIndex incrementalIndex;
    private final SaveIndexReader indexReader;
    private final String subcommand;

    public IncrementalCommand(PrintStream out, RenderCache cache, IncrementalRenderIndex incrementalIndex, SaveIndexReader indexReader, String subcommand) {
        this.out = out;
        this.cache = cache;
        this.incrementalIndex = incrementalIndex;
        this.indexReader = indexReader;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: incremental " + subcommand + " <save.vcdbs>");
        }
        Path savePath = Path.of(args[0]);
        switch (subcommand) {
            case "status" -> status(savePath);
            case "update" -> update(savePath);
            default -> throw new CommandException("Unknown incremental subcommand: " + subcommand);
        }

        return 0;
    }

    private void status(Path savePath) {
        ProgressReporter progress = new ProgressReporter(out);
        CacheKey key = cache.key(savePath);
        SaveIndex index = indexReader.read(savePath, progress);
        IncrementalState current = incrementalIndex.from(index, key);

        out.println("INCREMENTAL STATUS");
        out.println("Index exists: " + incrementalIndex.exists(key));
        if (!incrementalIndex.exists(key)) {
            out.println("Changed tables: all");
            return;
        }
        Map<String, String> changes = incrementalIndex.changes(incrementalIndex.read(key), current);
        out.println("Changed tables: " + changes.size());
        changes.forEach((table, state) -> out.println("  " + table + ": " + state));
    }

    private void update(Path savePath) {
        ProgressReporter progress = new ProgressReporter(out);
        CacheKey key = cache.key(savePath);
        SaveIndex index = indexReader.read(savePath, progress);
        incrementalIndex.write(key, incrementalIndex.from(index, key));

        out.println("INCREMENTAL INDEX UPDATED");
        out.println("Path: " + incrementalIndex.path(key));
    }
}
