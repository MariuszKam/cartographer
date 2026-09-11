package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public class HomeCommand
        implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final String subcommand;

    public HomeCommand(
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
        return switch (subcommand) {
            case "set" -> set(args);
            case "show" -> show(args);

            default -> throw new CommandException(
                    "Unknown home subcommand: "
                            + subcommand
            );
        };
    }

    private int set(
            String[] args
    ) {
        if (args.length != 1
                && args.length != 3) {

            throw new CommandException(
                    "Usage: home set <save.vcdbs> "
                            + "[<x> <z>]"
            );
        }

        Path savePath =
                Path.of(args[0]);

        HomeLocation home;

        if (args.length == 1) {
            home =
                    currentPlayerLocation(
                            savePath
                    );

        } else {
            home =
                    new HomeLocation(
                            parseDouble(
                                    args[1],
                                    "x"
                            ),
                            parseDouble(
                                    args[2],
                                    "z"
                            )
                    );
        }

        homeStore.save(
                savePath,
                home
        );

        out.println("HOME set");

        out.printf(
                Locale.ROOT,
                "X: %.3f%n",
                home.x()
        );

        out.printf(
                Locale.ROOT,
                "Z: %.3f%n",
                home.z()
        );

        return 0;
    }

    private int show(
            String[] args
    ) {
        if (args.length != 1) {
            throw new CommandException(
                    "Usage: home show <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(args[0]);

        HomeLocation home =
                homeStore
                        .load(savePath)
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "HOME is not set for this save"
                                        )
                        );

        out.println("HOME");

        out.printf(
                Locale.ROOT,
                "X: %.3f%n",
                home.x()
        );

        out.printf(
                Locale.ROOT,
                "Z: %.3f%n",
                home.z()
        );

        return 0;
    }

    private HomeLocation currentPlayerLocation(
            Path savePath
    ) {
        ProgressReporter progress =
                new ProgressReporter(out);

        WorldPosition absolute =
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

        DisplayPosition display =
                metadata.toDisplay(
                        absolute
                );

        return new HomeLocation(
                display.x(),
                display.z()
        );
    }

    private double parseDouble(
            String value,
            String name
    ) {
        try {
            return Double.parseDouble(value);

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + name
                            + ": "
                            + value
            );
        }
    }
}