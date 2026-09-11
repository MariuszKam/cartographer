package cartographer.cli;

import cartographer.model.HomeLocation;
import cartographer.model.WorldPosition;
import cartographer.navigation.Direction;
import cartographer.navigation.DirectionCalculator;
import cartographer.navigation.HomeStore;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

public class NavCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;
    private final HomeStore homeStore;
    private final String subcommand;
    private final DirectionCalculator directionCalculator = new DirectionCalculator();

    public NavCommand(PrintStream out, VcdbsReader reader, HomeStore homeStore, String subcommand) {
        this.out = out;
        this.reader = reader;
        this.homeStore = homeStore;
        this.subcommand = subcommand;
    }

    @Override
    public int run(String[] args) {
        if (!"home".equals(subcommand)) {
            throw new CommandException("Unknown nav subcommand: " + subcommand);
        }
        if (args.length < 1) {
            throw new CommandException("Usage: nav home <save.vcdbs>");
        }

        WorldPosition player = reader.readPlayerPosition(Path.of(args[0]), Optional.empty());
        HomeLocation home = homeStore.load().orElseThrow(() -> new CommandException("HOME is not set"));
        Direction direction = directionCalculator.fromPlayerToHome(player, home);

        out.printf("PLAYER: %.0f, %.0f%n", player.x(), player.z());
        out.printf("HOME:   %.0f, %.0f%n", home.x(), home.z());
        out.println();
        out.printf("Distance: %.0f blocks%n", direction.distanceBlocks());
        out.printf("Direction: %s%n", direction.compass());
        out.printf("Bearing: %.1f degrees%n", direction.bearingDegrees());
        return 0;
    }
}
