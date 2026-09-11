package cartographer.cli;

import cartographer.atlas.AtlasRenderer;
import cartographer.atlas.TilePyramid;
import cartographer.analysis.BlockScanner;
import cartographer.geology.GeologyAnalyzer;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.IncrementalRenderIndex;
import cartographer.perf.RenderCache;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.VcdbsReader;
import cartographer.save.SaveInspector;
import cartographer.save.SaveIndexReader;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;

public class CommandRouter {
    private final PrintStream out;
    private final PrintStream err;

    public CommandRouter() {
        this(System.out, System.err);
    }

    public CommandRouter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public int run(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                printHelp();
                return 0;
            }

            Command command = commandFor(args);
            return command.run(commandArgs(args));
        } catch (CommandException exception) {
            err.println("ERROR: " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            err.println("ERROR: " + exception.getMessage());
            return 1;
        }
    }

    private Command commandFor(String[] args) {
        VcdbsReader reader = new VcdbsReader(new PlayerDataParser(), new MapChunkParser(), new ChunkParser(), new RegistryParser());
        HomeStore homeStore = new HomeStore(Path.of(System.getProperty("user.home"), ".vs-cartographer", "home.properties"));
        MarkerStore markerStore = new MarkerStore(Path.of(System.getProperty("user.home"), ".vs-cartographer", "markers.csv"));
        Path cachePath = Path.of(System.getProperty("user.home"), ".vs-cartographer", "cache");
        RenderCache renderCache = new RenderCache(cachePath);

        return switch (args[0]) {
            case "whereami" -> new WhereamiCommand(out, reader);
            case "home" -> new HomeCommand(out, homeStore, subcommand(args, "home"));
            case "nav" -> new NavCommand(out, reader, homeStore, subcommand(args, "nav"));
            case "map" -> new MapCommand(out, reader, homeStore, new MapRenderer(), new PngWriter(), subcommand(args, "map"));
            case "scan" -> new ScanCommand(out, reader, new SurfaceScanner(), new BlockScanner(), subcommand(args, "scan"));
            case "geology" -> new GeologyCommand(out, reader, new SurfaceScanner(), new GeologyAnalyzer(), subcommand(args, "geology"));
            case "markers" -> new MarkerCommand(out, markerStore, subcommand(args, "markers"));
            case "cache" -> new CacheCommand(out, renderCache, new SaveIndexReader(), subcommand(args, "cache"));
            case "incremental" -> new IncrementalCommand(out, renderCache, new IncrementalRenderIndex(cachePath), new SaveIndexReader(), subcommand(args, "incremental"));
            case "atlas" -> new AtlasCommand(out, reader, homeStore, new AtlasRenderer(new TilePyramid(), new MapRenderer(), new PngWriter()), subcommand(args, "atlas"));
            case "inspect" -> new InspectCommand(out, new SaveInspector());
            case "index" -> new IndexCommand(out, new SaveIndexReader());
            default -> throw new CommandException("Unknown command: " + args[0]);
        };
    }

    private String[] commandArgs(String[] args) {
        if (args.length >= 2 && ("home".equals(args[0]) || "nav".equals(args[0]) || "map".equals(args[0]) || "scan".equals(args[0]) || "geology".equals(args[0]) || "markers".equals(args[0]) || "cache".equals(args[0]) || "incremental".equals(args[0]) || "atlas".equals(args[0]))) {
            return Arrays.copyOfRange(args, 2, args.length);
        }
        return Arrays.copyOfRange(args, 1, args.length);
    }

    private String subcommand(String[] args, String command) {
        if (args.length < 2) {
            throw new CommandException("Missing subcommand for " + command);
        }
        return args[1];
    }

    private void printHelp() {
        out.println("VS Cartographer");
        out.println();
        out.println("Usage:");
        out.println("  vs-cartographer whereami <save.vcdbs> [--player <id-or-name>]");
        out.println("  vs-cartographer home set <x> <z>");
        out.println("  vs-cartographer home show");
        out.println("  vs-cartographer nav home <save.vcdbs>");
        out.println("  vs-cartographer inspect <save.vcdbs>");
        out.println("  vs-cartographer index <save.vcdbs>");
        out.println("  vs-cartographer map render <save.vcdbs> --radius <blocks> --out <map.png> [--center-x <x> --center-z <z>] [--scale <n>] [--style simple|topographic|high-contrast] [--layers terrain,water,markers]");
        out.println("  vs-cartographer scan surface <save.vcdbs> --radius <blocks>");
        out.println("  vs-cartographer geology surface <save.vcdbs> --radius <blocks> [--center-x <x> --center-z <z>]");
        out.println("  vs-cartographer scan blocks <save.vcdbs> --match <text> [--center-x <x> --center-z <z>] [--radius <blocks>] [--limit <n>]");
        out.println("  vs-cartographer markers add <name> <x> <z>");
        out.println("  vs-cartographer markers list");
        out.println("  vs-cartographer cache warm <save.vcdbs>");
        out.println("  vs-cartographer cache status <save.vcdbs>");
        out.println("  vs-cartographer incremental status <save.vcdbs>");
        out.println("  vs-cartographer incremental update <save.vcdbs>");
        out.println("  vs-cartographer atlas render <save.vcdbs> --center-x <x> --center-z <z> --radius <blocks> --levels <n> --out <directory>");
    }
}
