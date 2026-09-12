package cartographer.environment;

import cartographer.model.IntDataMap2D;
import cartographer.model.ServerMapRegion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EnvironmentInterpreter {
    private static final int SAMPLE_LIMIT =
            8;

    private static final double COLD_TEMPERATURE_INDEX_MAX =
            85.0;

    private static final double HOT_TEMPERATURE_INDEX_MIN =
            170.0;

    private static final double ARID_RAINFALL_INDEX_MAX =
            85.0;

    private static final double HUMID_RAINFALL_INDEX_MIN =
            170.0;

    private static final double OPEN_FOREST_DENSITY_MAX =
            0.20;

    private static final double FORESTED_DENSITY_MIN =
            0.60;

    private final ClimateInterpreter climateInterpreter =
            new ClimateInterpreter();

    private final ForestInterpreter forestInterpreter =
            new ForestInterpreter();

    private final OceanInterpreter oceanInterpreter =
            new OceanInterpreter();

    private final LandformInterpreter landformInterpreter =
            new LandformInterpreter();

    public EnvironmentProfile interpret(
            ServerMapRegion region
    ) {
        Optional<ClimateSummary> climate =
                region.climateMap()
                        .map(
                                this::summarizeClimate
                        );

        Optional<ForestSummary> forest =
                region.forestMap()
                        .map(
                                this::summarizeForest
                        );

        Optional<OceanSummary> ocean =
                region.oceanMap()
                        .map(
                                this::summarizeOcean
                        );

        Optional<IdMapSummary> landform =
                region.landformMap()
                        .map(
                                this::summarizeLandforms
                        );

        Optional<IdMapSummary> geologicProvince =
                region.geologicProvinceMap()
                        .map(
                                this::summarizeIds
                        );

        EnumSet<EnvironmentLabel> labels =
                EnumSet.noneOf(
                        EnvironmentLabel.class
                );

        climate.ifPresent(
                summary ->
                        addClimateLabels(
                                labels,
                                summary
                        )
        );

        forest.ifPresent(
                summary ->
                        addForestLabels(
                                labels,
                                summary
                        )
        );

        return new EnvironmentProfile(
                region.coordinate(),
                climate,
                forest,
                ocean,
                landform,
                geologicProvince,
                Set.copyOf(
                        labels
                )
        );
    }

    private ClimateSummary summarizeClimate(
            IntDataMap2D map
    ) {
        int count =
                0;

        long temperatureTotal =
                0;

        long rainfallTotal =
                0;

        List<Integer> rawSample =
                new ArrayList<>();

        for (int z = map.innerMin();
             z < map.innerMaxExclusive();
             z++) {

            for (int x = map.innerMin();
                 x < map.innerMaxExclusive();
                 x++) {

                ClimateSample sample =
                        climateInterpreter.interpret(
                                map.valueAt(
                                        x,
                                        z
                                )
                        );

                if (rawSample.size()
                        < SAMPLE_LIMIT) {

                    rawSample.add(
                            sample.rawValue()
                    );
                }

                temperatureTotal +=
                        sample.temperatureIndex();

                rainfallTotal +=
                        sample.rainfallIndex();

                count++;
            }
        }

        return new ClimateSummary(
                count,
                average(
                        temperatureTotal,
                        count
                ),
                average(
                        rainfallTotal,
                        count
                ),
                List.copyOf(
                        rawSample
                )
        );
    }

    private ForestSummary summarizeForest(
            IntDataMap2D map
    ) {
        int count =
                0;

        int min =
                Integer.MAX_VALUE;

        int max =
                Integer.MIN_VALUE;

        double densityTotal =
                0.0;

        for (int z = map.innerMin();
             z < map.innerMaxExclusive();
             z++) {

            for (int x = map.innerMin();
                 x < map.innerMaxExclusive();
                 x++) {

                ForestSample sample =
                        forestInterpreter.interpret(
                                map.valueAt(
                                        x,
                                        z
                                )
                        );

                min =
                        Math.min(
                                min,
                                sample.rawValue()
                        );

                max =
                        Math.max(
                                max,
                                sample.rawValue()
                        );

                densityTotal +=
                        sample.normalizedDensity();

                count++;
            }
        }

        double averageDensity =
                count == 0
                        ? 0.0
                        : densityTotal / count;

        return new ForestSummary(
                count,
                minOrZero(
                        min,
                        count
                ),
                maxOrZero(
                        max,
                        count
                ),
                averageDensity,
                forestInterpreter.interpret(
                                (int) Math.round(
                                        averageDensity
                                                * 255.0
                                )
                        )
                        .densityClass()
        );
    }

    private OceanSummary summarizeOcean(
            IntDataMap2D map
    ) {
        int count =
                0;

        int min =
                Integer.MAX_VALUE;

        int max =
                Integer.MIN_VALUE;

        long total =
                0;

        for (int z = map.innerMin();
             z < map.innerMaxExclusive();
             z++) {

            for (int x = map.innerMin();
                 x < map.innerMaxExclusive();
                 x++) {

                OceanSample sample =
                        oceanInterpreter.interpret(
                                map.valueAt(
                                        x,
                                        z
                                )
                        );

                min =
                        Math.min(
                                min,
                                sample.rawValue()
                        );

                max =
                        Math.max(
                                max,
                                sample.rawValue()
                        );

                total +=
                        sample.rawValue();

                count++;
            }
        }

        return new OceanSummary(
                count,
                minOrZero(
                        min,
                        count
                ),
                maxOrZero(
                        max,
                        count
                ),
                average(
                        total,
                        count
                )
        );
    }

    private IdMapSummary summarizeLandforms(
            IntDataMap2D map
    ) {
        Map<Integer, Integer> counts =
                new HashMap<>();

        int count =
                0;

        for (int z = map.innerMin();
             z < map.innerMaxExclusive();
             z++) {

            for (int x = map.innerMin();
                 x < map.innerMaxExclusive();
                 x++) {

                LandformSample sample =
                        landformInterpreter.interpret(
                                map.valueAt(
                                        x,
                                        z
                                )
                        );

                counts.merge(
                        sample.rawId(),
                        1,
                        Integer::sum
                );

                count++;
            }
        }

        return idSummary(
                count,
                counts
        );
    }

    private IdMapSummary summarizeIds(
            IntDataMap2D map
    ) {
        Map<Integer, Integer> counts =
                new HashMap<>();

        int count =
                0;

        for (int z = map.innerMin();
             z < map.innerMaxExclusive();
             z++) {

            for (int x = map.innerMin();
                 x < map.innerMaxExclusive();
                 x++) {

                counts.merge(
                        map.valueAt(
                                x,
                                z
                        ),
                        1,
                        Integer::sum
                );

                count++;
            }
        }

        return idSummary(
                count,
                counts
        );
    }

    private IdMapSummary idSummary(
            int count,
            Map<Integer, Integer> counts
    ) {
        List<Integer> dominantIds =
                counts.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry
                                        .<Integer, Integer>comparingByValue()
                                        .reversed()
                                        .thenComparing(
                                                Map.Entry.comparingByKey()
                                        )
                        )
                        .limit(
                                SAMPLE_LIMIT
                        )
                        .map(
                                Map.Entry::getKey
                        )
                        .toList();

        return new IdMapSummary(
                count,
                counts.size(),
                dominantIds
        );
    }

    private void addClimateLabels(
            EnumSet<EnvironmentLabel> labels,
            ClimateSummary summary
    ) {
        if (summary.averageTemperatureIndex()
                <= COLD_TEMPERATURE_INDEX_MAX) {

            labels.add(
                    EnvironmentLabel.COLD
            );
        }

        if (summary.averageTemperatureIndex()
                >= HOT_TEMPERATURE_INDEX_MIN) {

            labels.add(
                    EnvironmentLabel.HOT
            );
        }

        if (summary.averageRainfallIndex()
                <= ARID_RAINFALL_INDEX_MAX) {

            labels.add(
                    EnvironmentLabel.ARID
            );
        }

        if (summary.averageRainfallIndex()
                >= HUMID_RAINFALL_INDEX_MIN) {

            labels.add(
                    EnvironmentLabel.HUMID
            );
        }
    }

    private void addForestLabels(
            EnumSet<EnvironmentLabel> labels,
            ForestSummary summary
    ) {
        if (summary.averageNormalizedDensity()
                <= OPEN_FOREST_DENSITY_MAX) {

            labels.add(
                    EnvironmentLabel.OPEN
            );
        }

        if (summary.averageNormalizedDensity()
                >= FORESTED_DENSITY_MIN) {

            labels.add(
                    EnvironmentLabel.FORESTED
            );
        }
    }

    private double average(
            long total,
            int count
    ) {
        return count == 0
                ? 0.0
                : total / (double) count;
    }

    private int minOrZero(
            int min,
            int count
    ) {
        return count == 0
                ? 0
                : min;
    }

    private int maxOrZero(
            int max,
            int count
    ) {
        return count == 0
                ? 0
                : max;
    }
}