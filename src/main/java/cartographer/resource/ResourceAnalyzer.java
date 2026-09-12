package cartographer.resource;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class ResourceAnalyzer {
    private static final int MAPCHUNK_SIZE_BLOCKS =
            32;

    private static final int MAPREGION_SIZE_BLOCKS =
            MapRegionCoordinate.SIZE_MAP_CHUNKS
                    * MAPCHUNK_SIZE_BLOCKS;

    public List<String> resourceKeys(
            List<ServerMapRegion> regions
    ) {
        Set<String> keys =
                new TreeSet<>();

        for (ServerMapRegion region : regions) {
            keys.addAll(
                    region.oreMaps()
                            .keySet()
            );
        }

        return List.copyOf(
                keys
        );
    }

    public List<String> matchingKeys(
            List<ServerMapRegion> regions,
            String query
    ) {
        String normalizedQuery =
                normalize(
                        query
                );

        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        List<String> keys =
                resourceKeys(
                        regions
                );

        List<String> exact =
                keys.stream()
                        .filter(
                                key ->
                                        normalize(
                                                key
                                        ).equals(
                                                normalizedQuery
                                        )
                                                || shortName(
                                                key
                                        ).equals(
                                                normalizedQuery
                                        )
                        )
                        .toList();

        if (!exact.isEmpty()) {
            return exact;
        }

        return keys.stream()
                .filter(
                        key ->
                                normalize(
                                        key
                                ).contains(
                                        normalizedQuery
                                )
                                        || shortName(
                                        key
                                ).contains(
                                        normalizedQuery
                                )
                )
                .toList();
    }

    public Optional<ResourceSummary> summarize(
            List<ServerMapRegion> regions,
            String resourceKey,
            int candidateLimit
    ) {
        List<Cell> cells =
                new ArrayList<>();

        Set<MapRegionCoordinate> regionsWithMap =
                new HashSet<>();

        Set<Integer> distinct =
                new HashSet<>();

        int min =
                Integer.MAX_VALUE;

        int max =
                Integer.MIN_VALUE;

        long total =
                0;

        for (ServerMapRegion region : regions) {
            IntDataMap2D map =
                    region.oreMaps()
                            .get(
                                    resourceKey
                            );

            if (map == null) {
                continue;
            }

            regionsWithMap.add(
                    region.coordinate()
            );

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

                    total += raw;

                    distinct.add(
                            raw
                    );

                    cells.add(
                            new Cell(
                                    region.coordinate(),
                                    map,
                                    x,
                                    z,
                                    raw
                            )
                    );
                }
            }
        }

        if (cells.isEmpty()) {
            return Optional.empty();
        }

        int rawMin =
                min;

        int rawMax =
                max;

        List<ResourceCandidate> strongest =
                cells.stream()
                        .sorted(
                                Comparator.comparingInt(
                                                Cell::rawValue
                                        )
                                        .reversed()
                                        .thenComparingInt(
                                                cell -> cell.region().x()
                                        )
                                        .thenComparingInt(
                                                cell -> cell.region().z()
                                        )
                                        .thenComparingInt(
                                                Cell::localZ
                                        )
                                        .thenComparingInt(
                                                Cell::localX
                                        )
                        )
                        .limit(
                                Math.max(
                                        0,
                                        candidateLimit
                                )
                        )
                        .map(
                                cell ->
                                        toCandidate(
                                                resourceKey,
                                                cell,
                                                rawMin,
                                                rawMax
                                        )
                        )
                        .toList();

        return Optional.of(
                new ResourceSummary(
                        resourceKey,
                        regionsWithMap.size(),
                        cells.size(),
                        rawMin,
                        rawMax,
                        total / (double) cells.size(),
                        distinct.size(),
                        strongest
                )
        );
    }

    private ResourceCandidate toCandidate(
            String resourceKey,
            Cell cell,
            int rawMin,
            int rawMax
    ) {
        double relativeIntensity =
                rawMax == rawMin
                        ? 0.0
                        : (cell.rawValue() - rawMin)
                        / (double) (rawMax - rawMin);

        return new ResourceCandidate(
                resourceKey,
                cell.region(),
                cell.localX(),
                cell.localZ(),
                cell.rawValue(),
                relativeIntensity,
                approximateWorldCoordinate(
                        cell.region().x(),
                        cell.map(),
                        cell.localX()
                ),
                approximateWorldCoordinate(
                        cell.region().z(),
                        cell.map(),
                        cell.localZ()
                )
        );
    }

    private int approximateWorldCoordinate(
            int regionCoordinate,
            IntDataMap2D map,
            int localCoordinate
    ) {
        int innerCoordinate =
                localCoordinate
                        - map.innerMin();

        double cellSize =
                MAPREGION_SIZE_BLOCKS
                        / (double) map.innerSize();

        return (int) Math.round(
                regionCoordinate
                        * (double) MAPREGION_SIZE_BLOCKS
                        + (innerCoordinate + 0.5)
                        * cellSize
        );
    }

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private String shortName(
            String value
    ) {
        String normalized =
                normalize(
                        value
                );

        int namespaceIndex =
                normalized.indexOf(
                        ':'
                );

        return namespaceIndex >= 0
                ? normalized.substring(
                namespaceIndex + 1
        )
                : normalized;
    }

    private record Cell(
            MapRegionCoordinate region,
            IntDataMap2D map,
            int localX,
            int localZ,
            int rawValue
    ) {
    }
}
