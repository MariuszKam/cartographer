package cartographer.cli;

import cartographer.marker.MarkerStore;
import cartographer.marker.UserMarker;

import java.io.PrintStream;

public class MarkerCommand implements Command {
    private final PrintStream out;
    private final MarkerStore markerStore;
    private final String subcommand;

    public MarkerCommand(PrintStream out, MarkerStore markerStore, String subcommand) {
        this.out = out;
        this.markerStore = markerStore;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        return switch (subcommand) {
            case "add" -> add(args);
            case "list" -> list();
            default -> throw new CommandException("Unknown markers subcommand: " + subcommand);
        };
    }

    private int add(String[] args) {
        if (args.length < 3) {
            throw new CommandException("Usage: markers add <name> <x> <z>");
        }
        UserMarker marker = new UserMarker(args[0], parseDouble(args[1], "x"), parseDouble(args[2], "z"));
        markerStore.add(marker);
        out.printf("Marker added: %s %.3f %.3f%n", marker.name(), marker.x(), marker.z());
        return 0;
    }

    private int list() {
        out.println("MARKERS");
        markerStore.load().forEach(marker -> out.printf("%s: %.3f, %.3f%n", marker.name(), marker.x(), marker.z()));
        return 0;
    }

    private double parseDouble(String value, String name) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid " + name + ": " + value);
        }
    }
}
