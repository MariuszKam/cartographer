package cartographer.cli;

import cartographer.atlas.AtlasRenderer;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AtlasCommand implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final HomeStore homeStore;
    private final AtlasRenderer atlasRenderer;
    private final String subcommand;

    public AtlasCommand(
            PrintStream out,
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            HomeStore homeStore,
            AtlasRenderer atlasRenderer,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.sessionFactory = sessionFactory;
        this.homeStore = homeStore;
        this.atlasRenderer = atlasRenderer;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (!"render".equals(
                subcommand
        )) {
            throw new CommandException(
                    "Unknown atlas subcommand: "
                            + subcommand
            );
        }

        if (args.length < 1) {
            throw new CommandException(
                    "Usage: atlas render <save.vcdbs> "
                            + "--center-x <x> "
                            + "--center-z <z> "
                            + "--radius <blocks> "
                            + "--levels <n> "
                            + "--out <directory>"
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

        Optional<WorldPosition> requestedCenter =
                center(
                        args
                );

        int radius =
                intOption(
                        args,
                        "--radius",
                        1024
                );

        int levels =
                intOption(
                        args,
                        "--levels",
                        3
                );

        Path output =
                Path.of(
                        requiredOutput(
                                args
                        )
                );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        WorldPosition center;
        HomeState home;
        List<MapChunk> chunks;

        try (SaveSession session =
                     sessionFactory.open(
                             savePath
                     )) {

            center =
                    requestedCenter.orElseGet(
                            () ->
                                    reader.readPlayerPosition(
                                            session,
                                            progress
                                    )
                    );

            home =
                    absoluteHome(
                            savePath,
                            session.snapshot()
                                    .metadata()
                    );

            chunks =
                    reader.readMapChunksAround(
                            session,
                            center,
                            radius,
                            diagnostics,
                            progress
                    );
        }

        atlasRenderer.render(
                output,
                center,
                home,
                chunks,
                radius,
                levels,
                progress
        );

        out.println(
                "ATLAS"
        );

        out.println(
                "Output: "
                        + output
        );

        out.println(
                "Levels: "
                        + levels
        );

        out.println(
                "Parsed mapchunks: "
                        + diagnostics.parsed()
        );

        out.println(
                "Skipped mapchunks: "
                        + diagnostics.skipped()
        );

        out.println(
                "Failed mapchunks: "
                        + diagnostics.failed()
        );

        if (!diagnostics.failureReasons()
                .isEmpty()) {

            out.println(
                    "Failure reasons:"
            );

            diagnostics.failureReasonLines()
                    .forEach(
                            line ->
                                    out.println(
                                            "  " + line
                                    )
                    );
        }

        diagnostics.notes()
                .forEach(
                        note ->
                                out.println(
                                        "Note: "
                                                + note
                                )
                );
    }

    private Optional<WorldPosition> center(
            String[] args
    ) {
        Optional<String> x =
                option(
                        args,
                        "--center-x"
                );

        Optional<String> z =
                option(
                        args,
                        "--center-z"
                );

        if (x.isEmpty()
                && z.isEmpty()) {

            return Optional.empty();
        }

        if (x.isEmpty()
                || z.isEmpty()) {

            throw new CommandException(
                    "--center-x and --center-z must be used together"
            );
        }

        return Optional.of(
                new WorldPosition(
                        parseDouble(
                                x.orElseThrow(),
                                "--center-x"
                        ),
                        0.0,
                        parseDouble(
                                z.orElseThrow(),
                                "--center-z"
                        )
                )
        );
    }

    private int intOption(
            String[] args,
            String optionName,
            int defaultValue
    ) {
        Optional<String> option =
                option(
                        args,
                        optionName
                );

        if (option.isEmpty()) {
            return defaultValue;
        }

        try {
            int value =
                    Integer.parseInt(
                            option.orElseThrow()
                    );

            int max =
                    "--levels".equals(
                            optionName
                    )
                            ? 8
                            : 8192;

            if (value <= 0
                    || value > max) {

                throw new CommandException(
                        optionName
                                + " must be between 1 and "
                                + max
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + option.orElse("")
            );
        }
    }

    private String requiredOutput(
            String[] args
    ) {
        return option(
                args,
                "--out"
        ).orElseThrow(
                () ->
                        new CommandException(
                                "Missing option: --out"
                        )
        );
    }

    private Optional<String> option(
            String[] args,
            String optionName
    ) {
        for (int index = 1;
             index < args.length - 1;
             index++) {

            if (optionName.equals(
                    args[index]
            )) {
                return Optional.of(
                        args[index + 1]
                );
            }
        }

        return Optional.empty();
    }

    private double parseDouble(
            String value,
            String optionName
    ) {
        try {
            return Double.parseDouble(
                    value
            );

        } catch (NumberFormatException exception) {
            throw new CommandException(
                    "Invalid "
                            + optionName
                            + ": "
                            + value
            );
        }
    }

    private HomeState absoluteHome(
            Path savePath,
            WorldMetadata metadata
    ) {
        Optional<HomeLocation> displayHome =
                homeStore.load(
                        savePath
                );

        if (displayHome.isEmpty()) {
            return HomeState.absent();
        }

        HomeLocation location =
                displayHome.orElseThrow();

        WorldPosition absolute =
                metadata.toAbsolute(
                        new DisplayPosition(
                                location.x(),
                                0.0,
                                location.z()
                        )
                );

        return HomeState.present(
                new HomeLocation(
                        absolute.x(),
                        absolute.z()
                )
        );
    }
}