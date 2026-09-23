package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

public class HomeCommand
        implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final HomeStore homeStore;
    private final String subcommand;

    public HomeCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            HomeStore homeStore,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.sessionFactory = sessionFactory;
        this.homeStore = homeStore;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        switch (subcommand) {
            case "set" ->
                    set(
                            args
                    );

            case "show" ->
                    show(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown home subcommand: "
                                    + subcommand
                    );
        }
    }

    private void set(
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
                Path.of(
                        args[0]
                );

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

        out.println(
                "HOME set"
        );

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
    }

    private void show(
            String[] args
    ) {
        if (args.length != 1) {
            throw new CommandException(
                    "Usage: home show <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        HomeLocation home =
                homeStore
                        .load(
                                savePath
                        )
                        .orElseThrow(
                                () ->
                                        new CommandException(
                                                "HOME is not set for this save"
                                        )
                        );

        out.println(
                "HOME"
        );

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
    }

    private HomeLocation currentPlayerLocation(
            Path savePath
    ) {
        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            WorldPosition absolute =
                    reader.readPlayerPosition(
                            session,
                            progress
                    );

            WorldMetadata metadata =
                    session.snapshot()
                            .metadata();

            DisplayPosition display =
                    metadata.toDisplay(
                            absolute
                    );

            return new HomeLocation(
                    display.x(),
                    display.z()
            );
        }
    }

    private double parseDouble(
            String value,
            String name
    ) {
        try {
            return Double.parseDouble(
                    value
            );

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