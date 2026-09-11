package cartographer.cli;

import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.save.VcdbsReader;
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

        return switch (args[0]) {
            case "whereami" -> new WhereamiCommand(out, reader);
            case "home" -> new HomeCommand(out, homeStore, subcommand(args, "home"));
            case "nav" -> new NavCommand(out, reader, homeStore, subcommand(args, "nav"));
            case "map" -> new MapCommand(out, reader, homeStore, new MapRenderer(), new PngWriter(), subcommand(args, "map"));
            case "scan" -> new ScanCommand(out, reader, new SurfaceScanner(), subcommand(args, "scan"));
            default -> throw new CommandException("Unknown command: " + args[0]);
        };
    }

    private String[] commandArgs(String[] args) {
        if (args.length >= 2 && ("home".equals(args[0]) || "nav".equals(args[0]) || "map".equals(args[0]) || "scan".equals(args[0]))) {
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
        out.println("  vs-cartographer map render <save.vcdbs> --radius <blocks> --out <map.png>");
        out.println("  vs-cartographer scan surface <save.vcdbs> --radius <blocks>");
    }
}
