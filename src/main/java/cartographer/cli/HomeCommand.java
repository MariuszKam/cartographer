package cartographer.cli;

import cartographer.model.HomeLocation;
import cartographer.navigation.HomeStore;

import java.io.PrintStream;

public class HomeCommand implements Command {
    private final PrintStream out;
    private final HomeStore homeStore;
    private final String subcommand;

    public HomeCommand(PrintStream out, HomeStore homeStore, String subcommand) {
        this.out = out;
        this.homeStore = homeStore;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        return switch (subcommand) {
            case "set" -> set(args);
            case "show" -> show();
            default -> throw new CommandException("Unknown home subcommand: " + subcommand);
        };
    }

    private int set(String[] args) {
        if (args.length < 2) {
            throw new CommandException("Usage: home set <x> <z>");
        }
        HomeLocation home = new HomeLocation(parseDouble(args[0], "x"), parseDouble(args[1], "z"));
        homeStore.save(home);
        out.printf("HOME set to %.3f, %.3f%n", home.x(), home.z());
        return 0;
    }

    private int show() {
        HomeLocation home = homeStore.load().orElseThrow(() -> new CommandException("HOME is not set"));
        out.println("HOME");
        out.printf("X: %.3f%n", home.x());
        out.printf("Z: %.3f%n", home.z());
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
