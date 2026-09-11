package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.Direction;
import cartographer.navigation.DirectionCalculator;
import cartographer.navigation.HomeStore;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public class NavCommand
        implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final String subcommand;

    private final DirectionCalculator directionCalculator =
            new DirectionCalculator();

    public NavCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.metadataReader = metadataReader;
        this.homeStore = homeStore;
        this.subcommand = subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        if (!"home".equals(subcommand)) {
            throw new CommandException(
                    "Unknown nav subcommand: "
                            + subcommand
            );
        }

        if (args.length != 1) {
            throw new CommandException(
                    "Usage: nav home <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(args[0]);

        ProgressReporter progress =
                new ProgressReporter(out);

        WorldPosition absolutePlayer =
                reader.readPlayerPosition(
                        savePath,
                        Optional.empty(),
                        progress
                );

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        DisplayPosition player =
                metadata.toDisplay(
                        absolutePlayer
                );

        HomeLocation home =
                homeStore
                        .load(savePath)
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "HOME is not set for this save"
                                        )
                        );

        Direction direction =
                directionCalculator
                        .fromPlayerToHome(
                                player,
                                home
                        );

        out.println();
        out.println("NAVIGATION");
        out.println();

        out.printf(
                Locale.ROOT,
                "PLAYER: %.1f, %.1f%n",
                player.x(),
                player.z()
        );

        out.printf(
                Locale.ROOT,
                "HOME:   %.1f, %.1f%n",
                home.x(),
                home.z()
        );

        out.println();

        out.printf(
                Locale.ROOT,
                "Distance: %.0f blocks%n",
                direction.distanceBlocks()
        );

        out.printf(
                "Direction: %s%n",
                direction.compass()
        );

        out.printf(
                Locale.ROOT,
                "Bearing: %.1f degrees%n",
                direction.bearingDegrees()
        );

        return 0;
    }
}