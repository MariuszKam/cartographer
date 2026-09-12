package cartographer.geology;

import cartographer.model.IntDataMap2D;
import cartographer.model.ServerMapRegion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RockStrataAnalyzer {
    private static final int DOMINANT_LIMIT =
            8;

    public RockStrataSummary summarize(
            ServerMapRegion region
    ) {
        return new RockStrataSummary(
                region.coordinate(),
                summaries(
                        region.rockStrata()
                )
        );
    }

    private List<RockStratumSummary> summaries(
            List<IntDataMap2D> maps
    ) {
        return java.util.stream.IntStream.range(
                        0,
                        maps.size()
                )
                .mapToObj(
                        index ->
                                summarize(
                                        index,
                                        maps.get(
                                                index
                                        )
                                )
                )
                .toList();
    }

    private RockStratumSummary summarize(
            int index,
            IntDataMap2D map
    ) {
        Map<Integer, Integer> counts =
                new HashMap<>();

        int min =
                Integer.MAX_VALUE;

        int max =
                Integer.MIN_VALUE;

        int samples =
                0;

        for (int z = map.innerMin(); z < map.innerMaxExclusive(); z++) {
            for (int x = map.innerMin(); x < map.innerMaxExclusive(); x++) {
                int raw =
                        map.valueAt(
                                x,
                                z
                        );

                min =
                        Math.min(
                                min,
                                raw
                        );

                max =
                        Math.max(
                                max,
                                raw
                        );

                counts.merge(
                        raw,
                        1,
                        Integer::sum
                );

                samples++;
            }
        }

        List<Integer> dominant =
                counts.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.<Integer, Integer>comparingByValue()
                                        .reversed()
                                        .thenComparing(
                                                Map.Entry.comparingByKey()
                                        )
                        )
                        .limit(
                                DOMINANT_LIMIT
                        )
                        .map(
                                Map.Entry::getKey
                        )
                        .toList();

        return new RockStratumSummary(
                index,
                map.size(),
                map.topLeftPadding(),
                map.bottomRightPadding(),
                map.innerSize(),
                samples,
                samples == 0 ? 0 : min,
                samples == 0 ? 0 : max,
                counts.size(),
                dominant
        );
    }
}
