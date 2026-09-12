package cartographer.geology;

import cartographer.model.IntDataMap2D;
import cartographer.model.ServerMapRegion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GeologicProvinceInterpreter {
    private static final int DOMINANT_LIMIT =
            8;

    public Optional<GeologicProvinceSummary> summarize(
            ServerMapRegion region
    ) {
        return region.geologicProvinceMap()
                .map(
                        map ->
                                summarize(
                                        region,
                                        map
                                )
                );
    }

    private GeologicProvinceSummary summarize(
            ServerMapRegion region,
            IntDataMap2D map
    ) {
        Map<Integer, Integer> counts =
                new HashMap<>();

        int samples =
                0;

        for (int z = map.innerMin(); z < map.innerMaxExclusive(); z++) {
            for (int x = map.innerMin(); x < map.innerMaxExclusive(); x++) {
                counts.merge(
                        map.valueAt(
                                x,
                                z
                        ),
                        1,
                        Integer::sum
                );

                samples++;
            }
        }

        List<Integer> dominantIds =
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

        return new GeologicProvinceSummary(
                region.coordinate(),
                samples,
                counts.size(),
                dominantIds
        );
    }
}
