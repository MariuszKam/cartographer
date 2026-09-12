package cartographer.cli;

import cartographer.model.IntDataMap2D;
import cartographer.model.ServerMapRegion;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapRegionCommand implements Command {

    private static final int SAMPLE_LIMIT =
            8;

    private final PrintStream out;
    private final VcdbsReader reader;
    private final String subcommand;

    public MapRegionCommand(
            PrintStream out,
            VcdbsReader reader,
            String subcommand
    ) {
        this.out = out;
        this.reader = reader;
        this.subcommand = subcommand;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (!"inspect".equals(
                subcommand
        )) {
            throw new CommandException(
                    "Unknown mapregion subcommand: "
                            + subcommand
            );
        }

        if (args.length < 1) {
            throw new CommandException(
                    "Usage: mapregion inspect <save.vcdbs>"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        List<ServerMapRegion> regions =
                reader.readMapRegions(
                        savePath,
                        diagnostics,
                        progress
                );

        out.println(
                "MAPREGION"
        );

        out.println(
                "Rows parsed: "
                        + diagnostics.parsed()
        );

        out.println(
                "Rows skipped: "
                        + diagnostics.skipped()
        );

        out.println(
                "Rows failed: "
                        + diagnostics.failed()
        );

        printFailureReasons(
                diagnostics
        );

        for (ServerMapRegion region :
                regions) {

            out.println();

            out.println(
                    "Region "
                            + region.coordinate().x()
                            + ","
                            + region.coordinate().z()
            );

            region.climateMap()
                    .ifPresentOrElse(
                            map ->
                                    printMap(
                                            "ClimateMap",
                                            map
                                    ),
                            () ->
                                    printMissingMap(
                                            "ClimateMap"
                                    )
                    );

            region.forestMap()
                    .ifPresentOrElse(
                            map ->
                                    printMap(
                                            "ForestMap",
                                            map
                                    ),
                            () ->
                                    printMissingMap(
                                            "ForestMap"
                                    )
                    );

            region.landformMap()
                    .ifPresentOrElse(
                            map ->
                                    printMap(
                                            "LandformMap",
                                            map
                                    ),
                            () ->
                                    printMissingMap(
                                            "LandformMap"
                                    )
                    );

            region.geologicProvinceMap()
                    .ifPresentOrElse(
                            map ->
                                    printMap(
                                            "GeologicProvinceMap",
                                            map
                                    ),
                            () ->
                                    printMissingMap(
                                            "GeologicProvinceMap"
                                    )
                    );

            region.oceanMap()
                    .ifPresentOrElse(
                            map ->
                                    printMap(
                                            "OceanMap",
                                            map
                                    ),
                            () ->
                                    printMissingMap(
                                            "OceanMap"
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

    private void printMap(
            String name,
            IntDataMap2D map
    ) {
        MapStats stats =
                MapStats.from(
                        map
                );

        out.println(
                "  "
                        + name
                        + ": size="
                        + map.width()
                        + "x"
                        + map.height()
                        + " values="
                        + map.valueCount()
                        + " padding="
                        + map.topLeftPadding()
                        + "/"
                        + map.bottomRightPadding()
                        + " innerSize="
                        + map.innerSize()
                        + " min="
                        + stats.min()
                        + " max="
                        + stats.max()
                        + " distinct="
                        + stats.distinctCount()
                        + " sample="
                        + stats.sample()
        );
    }

    private void printMissingMap(
            String name
    ) {
        out.println(
                "  "
                        + name
                        + ": missing"
        );
    }

    private void printFailureReasons(
            ReadDiagnostics diagnostics
    ) {
        if (diagnostics.failureReasons()
                .isEmpty()) {

            return;
        }

        out.println(
                "Failure reasons:"
        );

        diagnostics.failureReasonLines()
                .forEach(
                        line ->
                                out.println(
                                        "  "
                                                + line
                                )
                );
    }

    private record MapStats(
            int min,
            int max,
            int distinctCount,
            List<Integer> sample
    ) {

        static MapStats from(
                IntDataMap2D map
        ) {
            int[] data =
                    map.data();

            int min =
                    Integer.MAX_VALUE;

            int max =
                    Integer.MIN_VALUE;

            Set<Integer> distinct =
                    new HashSet<>();

            for (int value : data) {
                min =
                        Math.min(
                                min,
                                value
                        );

                max =
                        Math.max(
                                max,
                                value
                        );

                distinct.add(
                        value
                );
            }

            List<Integer> sample =
                    java.util.Arrays.stream(
                                    data
                            )
                            .limit(
                                    SAMPLE_LIMIT
                            )
                            .boxed()
                            .toList();

            return new MapStats(
                    min,
                    max,
                    distinct.size(),
                    sample
            );
        }
    }
}