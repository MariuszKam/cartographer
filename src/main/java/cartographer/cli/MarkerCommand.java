package cartographer.cli;

import cartographer.marker.MarkerStore;
import cartographer.marker.UserMarker;
import cartographer.model.DisplayPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MarkerCommand implements Command {

    private final PrintStream out;

    private final MarkerStore markerStore;

    private final VcdbsReader reader;

    private final WorldMetadataReader metadataReader;

    private final String subcommand;

    public MarkerCommand(
            PrintStream out,
            MarkerStore markerStore,
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            String subcommand
    ) {
        this.out =
                out;

        this.markerStore =
                markerStore;

        this.reader =
                reader;

        this.metadataReader =
                metadataReader;

        this.subcommand =
                subcommand;
    }

    @Override
    public int run(
            String[] args
    ) {
        return switch (subcommand) {
            case "add" ->
                    add(
                            args
                    );

            case "here" ->
                    here(
                            args
                    );

            case "list" ->
                    list(
                            args
                    );

            case "remove" ->
                    remove(
                            args
                    );

            case "clear" ->
                    clear(
                            args
                    );

            default ->
                    throw new CommandException(
                            "Unknown markers subcommand: "
                                    + subcommand
                    );
        };
    }

    private int add(
            String[] args
    ) {
        if (args.length < 4) {
            throw new CommandException(
                    "Usage: markers add <save.vcdbs> <name> <x> <z>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        UserMarker marker =
                new UserMarker(
                        args[1],
                        parseDouble(
                                args[2],
                                "x"
                        ),
                        parseDouble(
                                args[3],
                                "z"
                        )
                );

        markerStore.put(
                savePath,
                marker
        );

        out.println(
                "MARKER SAVED"
        );

        printMarker(
                marker
        );

        out.println(
                "Coordinate space: DISPLAY"
        );

        return 0;
    }

    private int here(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: markers here <save.vcdbs> <name>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition player =
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
                        player
                );

        UserMarker marker =
                new UserMarker(
                        args[1],
                        display.x(),
                        display.z()
                );

        markerStore.put(
                savePath,
                marker
        );

        out.println(
                "MARKER SAVED AT PLAYER"
        );

        printMarker(
                marker
        );

        out.println(
                "Coordinate space: DISPLAY"
        );

        return 0;
    }

    private int list(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: markers list <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        List<UserMarker> markers =
                markerStore.load(
                        savePath
                );

        out.println(
                "MARKERS"
        );

        out.println(
                "Coordinate space: DISPLAY"
        );

        out.println(
                "Count: "
                        + markers.size()
        );

        if (markers.isEmpty()) {
            out.println(
                    "  none"
            );

            return 0;
        }

        for (UserMarker marker : markers) {
            out.printf(
                    Locale.ROOT,
                    "  %s: %.3f, %.3f%n",
                    marker.name(),
                    marker.x(),
                    marker.z()
            );
        }

        return 0;
    }

    private int remove(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: markers remove <save.vcdbs> <name>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String name =
                args[1];

        boolean removed =
                markerStore.remove(
                        savePath,
                        name
                );

        if (!removed) {
            out.println(
                    "Marker not found: "
                            + name
            );

            return 0;
        }

        out.println(
                "Marker removed: "
                        + name
        );

        return 0;
    }

    private int clear(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: markers clear <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        int removed =
                markerStore.clear(
                        savePath
                );

        out.println(
                "Markers cleared: "
                        + removed
        );

        return 0;
    }

    private void printMarker(
            UserMarker marker
    ) {
        out.printf(
                Locale.ROOT,
                "%s: %.3f, %.3f%n",
                marker.name(),
                marker.x(),
                marker.z()
        );
    }

    private double parseDouble(
            String value,
            String name
    ) {
        try {
            double parsed =
                    Double.parseDouble(
                            value
                    );

            if (!Double.isFinite(
                    parsed
            )) {
                throw new NumberFormatException();
            }

            return parsed;

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