package cartographer.cli;

import cartographer.analysis.BlockScanner;
import cartographer.atlas.AtlasRenderer;
import cartographer.atlas.TilePyramid;
import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.environment.EnvironmentInterpreter;
import cartographer.geology.GeologyAnalyzer;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.IncrementalRenderIndex;
import cartographer.perf.RenderCache;
import cartographer.render.MapRenderer;
import cartographer.render.PngWriter;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.save.SaveIndexReader;
import cartographer.save.SaveInspector;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.SurfaceScanner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;

public class CommandRouter {

    private final PrintStream out;

    private final PrintStream err;

    public CommandRouter() {
        this(
                System.out,
                System.err
        );
    }

    public CommandRouter(
            PrintStream out,
            PrintStream err
    ) {
        this.out =
                out;

        this.err =
                err;
    }

    public int run(
            String[] args
    ) {
        try {
            if (args.length == 0
                    || "--help".equals(
                    args[0]
            )
                    || "-h".equals(
                    args[0]
            )) {

                printHelp();

                return 0;
            }

            Command command =
                    commandFor(
                            args
                    );

            return command.run(
                    commandArgs(
                            args
                    )
            );

        } catch (CommandException exception) {
            err.println(
                    "ERROR: "
                            + exception.getMessage()
            );

            return 2;

        } catch (Exception exception) {
            err.println(
                    "ERROR: "
                            + exception.getMessage()
            );

            return 1;
        }
    }

    private Command commandFor(
            String[] args
    ) {
        VcdbsReader reader =
                new VcdbsReader(
                        new PlayerDataParser(),
                        new MapChunkParser(),
                        new ChunkParser(),
                        new RegistryParser()
                );

        WorldMetadataReader metadataReader =
                new WorldMetadataReader();

        Path configDirectory =
                Path.of(
                        System.getProperty(
                                "user.home"
                        ),
                        ".vs-cartographer"
                );

        HomeStore homeStore =
                new HomeStore(
                        configDirectory.resolve(
                                "home.properties"
                        )
                );

        MarkerStore markerStore =
                new MarkerStore(
                        configDirectory.resolve(
                                "markers.csv"
                        )
                );

        Path cachePath =
                configDirectory.resolve(
                        "cache"
                );

        RenderCache renderCache =
                new RenderCache(
                        cachePath
                );

        return switch (args[0]) {
            case "whereami" ->
                    new WhereamiCommand(
                            out,
                            reader,
                            metadataReader
                    );

            case "home" ->
                    new HomeCommand(
                            out,
                            reader,
                            metadataReader,
                            homeStore,
                            subcommand(
                                    args,
                                    "home"
                            )
                    );

            case "nav" ->
                    new NavCommand(
                            out,
                            reader,
                            metadataReader,
                            homeStore,
                            subcommand(
                                    args,
                                    "nav"
                            )
                    );

            case "map" ->
                    new MapCommand(
                            out,
                            reader,
                            metadataReader,
                            homeStore,
                            markerStore,
                            new MapRenderer(),
                            new UserMarkerRenderer(),
                            new PngWriter(),
                            subcommand(
                                    args,
                                    "map"
                            )
                    );

            case "mapregion" ->
                    new MapRegionCommand(
                            out,
                            reader,
                            subcommand(
                                    args,
                                    "mapregion"
                            )
                    );

            case "environment" ->
                    new EnvironmentCommand(
                            out,
                            reader,
                            new EnvironmentInterpreter(),
                            subcommand(
                                    args,
                                    "environment"
                            )
                    );

            case "resource" ->
                    new ResourceCommand(
                            out,
                            reader,
                            new ResourceAnalyzer(),
                            subcommand(
                                    args,
                                    "resource"
                            )
                    );

            case "coverage" ->
                    new CoverageCommand(
                            out,
                            reader,
                            metadataReader,
                            homeStore,
                            new RegionCoverageAnalyzer(),
                            new RegionCoverageRenderer(),
                            new PngWriter(),
                            subcommand(
                                    args,
                                    "coverage"
                            )
                    );

            case "scan" ->
                    new ScanCommand(
                            out,
                            reader,
                            new SurfaceScanner(),
                            new BlockScanner(),
                            subcommand(
                                    args,
                                    "scan"
                            )
                    );

            case "geology" ->
                    new GeologyCommand(
                            out,
                            reader,
                            new SurfaceScanner(),
                            new GeologyAnalyzer(),
                            subcommand(
                                    args,
                                    "geology"
                            )
                    );

            case "markers" ->
                    new MarkerCommand(
                            out,
                            markerStore,
                            reader,
                            metadataReader,
                            subcommand(
                                    args,
                                    "markers"
                            )
                    );

            case "cache" ->
                    new CacheCommand(
                            out,
                            renderCache,
                            new SaveIndexReader(),
                            subcommand(
                                    args,
                                    "cache"
                            )
                    );

            case "incremental" ->
                    new IncrementalCommand(
                            out,
                            renderCache,
                            new IncrementalRenderIndex(
                                    cachePath
                            ),
                            new SaveIndexReader(),
                            subcommand(
                                    args,
                                    "incremental"
                            )
                    );

            case "atlas" ->
                    new AtlasCommand(
                            out,
                            reader,
                            metadataReader,
                            homeStore,
                            new AtlasRenderer(
                                    new TilePyramid(),
                                    new MapRenderer(),
                                    new PngWriter()
                            ),
                            subcommand(
                                    args,
                                    "atlas"
                            )
                    );

            case "inspect" ->
                    new InspectCommand(
                            out,
                            new SaveInspector()
                    );

            case "index" ->
                    new IndexCommand(
                            out,
                            new SaveIndexReader()
                    );

            default ->
                    throw new CommandException(
                            "Unknown command: "
                                    + args[0]
                    );
        };
    }

    private String[] commandArgs(
            String[] args
    ) {
        if (args.length >= 2
                && hasSubcommand(
                args[0]
        )) {

            return Arrays.copyOfRange(
                    args,
                    2,
                    args.length
            );
        }

        return Arrays.copyOfRange(
                args,
                1,
                args.length
        );
    }

    private boolean hasSubcommand(
            String command
    ) {
        return switch (command) {
            case "home",
                 "nav",
                 "map",
                 "scan",
                 "geology",
                 "markers",
                 "cache",
                 "incremental",
                 "atlas",
                 "mapregion",
                 "environment",
                 "resource",
                 "coverage" -> true;

            default -> false;
        };
    }

    private String subcommand(
            String[] args,
            String command
    ) {
        if (args.length < 2) {
            throw new CommandException(
                    "Missing subcommand for "
                            + command
            );
        }

        return args[1];
    }

    private void printHelp() {
        out.println(
                "VS Cartographer"
        );

        out.println();

        out.println(
                "Navigation:"
        );

        out.println(
                "  vs-cartographer whereami <save.vcdbs> "
                        + "[--player <id-or-name>]"
        );

        out.println(
                "  vs-cartographer home set <save.vcdbs> "
                        + "[<display-x> <display-z>]"
        );

        out.println(
                "  vs-cartographer home show <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer nav home <save.vcdbs>"
        );

        out.println();

        out.println(
                "Cartography:"
        );

        out.println(
                "  vs-cartographer map render <save.vcdbs> "
                        + "--radius <blocks> "
                        + "--out <map.png> "
                        + "[--center-x <x> --center-z <z>] "
                        + "[--scale <n>] "
                        + "[--style simple|topographic|high-contrast] "
                        + "[--layers terrain,surface,environment,geology,markers]"
        );

        out.println(
                "  vs-cartographer coverage inspect <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer coverage render <save.vcdbs> "
                        + "--out <coverage.png>"
        );

        out.println(
                "  vs-cartographer mapregion inspect <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer environment inspect <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer geology surface <save.vcdbs> "
                        + "--radius <blocks> "
                        + "[--center-x <x> --center-z <z>]"
        );

        out.println(
                "  vs-cartographer geology strata <save.vcdbs>"
        );

        out.println();

        out.println(
                "Markers:"
        );

        out.println(
                "  vs-cartographer markers add <save.vcdbs> "
                        + "<name...> <display-x> <display-z>"
        );

        out.println(
                "  vs-cartographer markers update <save.vcdbs> "
                        + "<name...> <display-x> <display-z>"
        );

        out.println(
                "  vs-cartographer markers here <save.vcdbs> <name...>"
        );

        out.println(
                "  vs-cartographer markers list <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer markers remove <save.vcdbs> <name...>"
        );

        out.println(
                "  vs-cartographer markers clear <save.vcdbs>"
        );

        out.println();

        out.println(
                "Resources and scanning:"
        );

        out.println(
                "  vs-cartographer resource list <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer resource inspect <save.vcdbs> <resource>"
        );

        out.println(
                "  vs-cartographer resource search <save.vcdbs> <resource> "
                        + "[--top <n>] [--separation <blocks>]"
        );

        out.println(
                "  vs-cartographer resource render <save.vcdbs> <resource> "
                        + "[--radius <blocks>] [--out <map.png>]"
        );

        out.println(
                "  vs-cartographer resource surface-search <save.vcdbs> <match> "
                        + "[--radius <blocks>] [--top <n>]"
        );

        out.println(
                "  vs-cartographer resource surface-render <save.vcdbs> <match> "
                        + "[--radius <blocks>] [--out <map.png>]"
        );

        out.println(
                "  vs-cartographer scan surface <save.vcdbs> "
                        + "--radius <blocks>"
        );

        out.println(
                "  vs-cartographer scan blocks <save.vcdbs> "
                        + "--match <text> "
                        + "[--center-x <x> --center-z <z>] "
                        + "[--radius <blocks>] "
                        + "[--limit <n>]"
        );

        out.println();

        out.println(
                "Save diagnostics:"
        );

        out.println(
                "  vs-cartographer inspect <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer index <save.vcdbs>"
        );

        out.println();

        out.println(
                "Cache and atlas:"
        );

        out.println(
                "  vs-cartographer cache warm <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer cache status <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer incremental status <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer incremental update <save.vcdbs>"
        );

        out.println(
                "  vs-cartographer atlas render <save.vcdbs> "
                        + "--center-x <x> "
                        + "--center-z <z> "
                        + "--radius <blocks> "
                        + "--levels <n> "
                        + "--out <directory>"
        );

        out.println();

        out.println(
                "Map layers:"
        );

        out.println(
                "  TERRAIN      base height/topography"
        );

        out.println(
                "  SURFACE      semantic surface and real liquid/water data"
        );

        out.println(
                "  ENVIRONMENT  Cartographer-derived environment overlay"
        );

        out.println(
                "  GEOLOGY      raw geologic-province category overlay"
        );

        out.println(
                "  MARKERS      PLAYER, HOME and user markers"
        );

        out.println();

        out.println(
                "Default map layers: terrain,surface,markers"
        );

        out.println(
                "Legacy layer alias: water -> surface"
        );
    }
}