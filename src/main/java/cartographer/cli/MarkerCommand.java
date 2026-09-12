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
import java.util.Arrays;
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
        switch (subcommand) {
            case "add" ->
                    add(
                            args
                    );

            case "update" ->
                    update(
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
        }

        return 0;
    }

    /*
     * Syntax intentionally allows an unquoted multi-word marker name.
     *
     * Example arguments after routing:
     *
     * world.vcdbs RED CLAY -834 259
     *
     * The final two arguments are always coordinates.
     * Everything between savePath and those coordinates becomes the name.
     */
    private void add(
            String[] args
    ) {
        if (args.length < 4) {
            throw new CommandException(
                    "Usage: markers add <save.vcdbs> "
                            + "<name...> <x> <z>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String name =
                joinArguments(
                        args,
                        1,
                        args.length - 2
                );

        double x =
                parseDouble(
                        args[args.length - 2],
                        "x"
                );

        double z =
                parseDouble(
                        args[args.length - 1],
                        "z"
                );

        UserMarker marker =
                new UserMarker(
                        name,
                        x,
                        z
                );

        boolean replacing =
                findByName(
                        markerStore.load(
                                savePath
                        ),
                        name
                ).isPresent();

        markerStore.put(
                savePath,
                marker
        );

        out.println(
                replacing
                        ? "MARKER REPLACED"
                        : "MARKER ADDED"
        );

        printMarker(
                marker
        );

        out.println(
                "Coordinate space: DISPLAY"
        );
    }

    /*
     * Explicit update differs from add/upsert:
     * update requires that the marker already exists.
     */
    private void update(
            String[] args
    ) {
        if (args.length < 4) {
            throw new CommandException(
                    "Usage: markers update <save.vcdbs> "
                            + "<name...> <x> <z>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String name =
                joinArguments(
                        args,
                        1,
                        args.length - 2
                );

        Optional<UserMarker> existing =
                findByName(
                        markerStore.load(
                                savePath
                        ),
                        name
                );

        if (existing.isEmpty()) {
            throw new CommandException(
                    "Marker not found: "
                            + name
            );
        }

        UserMarker marker =
                new UserMarker(
                        existing.get()
                                .name(),
                        parseDouble(
                                args[args.length - 2],
                                "x"
                        ),
                        parseDouble(
                                args[args.length - 1],
                                "z"
                        )
                );

        markerStore.put(
                savePath,
                marker
        );

        out.println(
                "MARKER UPDATED"
        );

        printMarker(
                marker
        );

        out.println(
                "Coordinate space: DISPLAY"
        );
    }

    /*
     * Everything after savePath is considered the marker name.
     *
     * That makes:
     *
     * markers here world.vcdbs OLD COPPER MINE
     *
     * work without nested quoting.
     */
    private void here(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: markers here <save.vcdbs> <name...>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String name =
                joinArguments(
                        args,
                        1,
                        args.length
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
                        name,
                        display.x(),
                        display.z()
                );

        boolean replacing =
                findByName(
                        markerStore.load(
                                savePath
                        ),
                        name
                ).isPresent();

        markerStore.put(
                savePath,
                marker
        );

        out.println(
                replacing
                        ? "MARKER MOVED TO PLAYER"
                        : "MARKER SAVED AT PLAYER"
        );

        printMarker(
                marker
        );

        out.println(
                "Coordinate space: DISPLAY"
        );
    }

    private void list(
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

            return;
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
    }

    private void remove(
            String[] args
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Usage: markers remove <save.vcdbs> <name...>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        String name =
                joinArguments(
                        args,
                        1,
                        args.length
                );

        Optional<UserMarker> existing =
                findByName(
                        markerStore.load(
                                savePath
                        ),
                        name
                );

        if (existing.isEmpty()) {
            out.println(
                    "Marker not found: "
                            + name
            );

            return;
        }

        markerStore.remove(
                savePath,
                existing.get()
                        .name()
        );

        out.println(
                "Marker removed: "
                        + existing.get()
                        .name()
        );
    }

    private void clear(
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
    }

    private Optional<UserMarker> findByName(
            List<UserMarker> markers,
            String name
    ) {
        return markers.stream()
                .filter(
                        marker ->
                                marker.name()
                                        .equalsIgnoreCase(
                                                name
                                        )
                )
                .findFirst();
    }

    private String joinArguments(
            String[] args,
            int startInclusive,
            int endExclusive
    ) {
        if (startInclusive < 0
                || endExclusive > args.length
                || startInclusive >= endExclusive) {

            throw new CommandException(
                    "Marker name is required"
            );
        }

        String name =
                String.join(
                                " ",
                                Arrays.copyOfRange(
                                        args,
                                        startInclusive,
                                        endExclusive
                                )
                        )
                        .trim();

        if (name.isBlank()) {
            throw new CommandException(
                    "Marker name is required"
            );
        }

        return name;
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
