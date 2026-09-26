package cartographer.resource;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;

import java.util.ArrayList;
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

    public List<ResourceOverlayCell> overlayCells(
            List<ServerMapRegion> regions,
            String resourceKey,
            double minimumRelativeSignal
    ) {
        if (minimumRelativeSignal < 0.0
                || minimumRelativeSignal > 1.0) {

            throw new IllegalArgumentException(
                    "minimumRelativeSignal must be between 0 and 1"
            );
        }

        Optional<ResourceDataset> dataset =
                dataset(
                        regions,
                        resourceKey
                );

        if (dataset.isEmpty()) {
            return List.of();
        }

        ResourceDataset data =
                dataset.get();

        List<ResourceOverlayCell> result =
                new ArrayList<>();

        for (Cell cell : data.cells()) {
            double relative =
                    relativeIntensity(
                            cell.rawValue(),
                            data.rawMin(),
                            data.rawMax()
                    );

            if (relative
                    < minimumRelativeSignal) {

                continue;
            }

            result.add(
                    toOverlayCell(
                            resourceKey,
                            cell,
                            relative
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private Optional<ResourceDataset> dataset(
            List<ServerMapRegion> regions,
            String resourceKey
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

            for (int z = map.innerMin();
                 z < map.innerMaxExclusive();
                 z++) {

                for (int x = map.innerMin();
                     x < map.innerMaxExclusive();
                     x++) {

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

                    total +=
                            raw;

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

        return Optional.of(
                new ResourceDataset(
                        List.copyOf(
                                cells
                        ),
                        regionsWithMap.size(),
                        min,
                        max,
                        total
                                / (double) cells.size(),
                        distinct.size()
                )
        );
    }

    private ResourceOverlayCell toOverlayCell(
            String resourceKey,
            Cell cell,
            double relativeIntensity
    ) {
        double minX =
                worldCellMinimum(
                        cell.region().x(),
                        cell.map(),
                        cell.localX()
                );

        double minZ =
                worldCellMinimum(
                        cell.region().z(),
                        cell.map(),
                        cell.localZ()
                );

        double cellSize =
                cellSizeBlocks(
                        cell.map()
                );

        return new ResourceOverlayCell(
                resourceKey,
                cell.region(),
                cell.localX(),
                cell.localZ(),
                cell.rawValue(),
                relativeIntensity,
                minX,
                minZ,
                minX + cellSize,
                minZ + cellSize
        );
    }

    private double relativeIntensity(
            int rawValue,
            int rawMin,
            int rawMax
    ) {
        if (rawMax == rawMin) {
            return 0.0;
        }

        return (rawValue - rawMin)
                / (double) (
                rawMax - rawMin
        );
    }

    private double worldCellMinimum(
            int regionCoordinate,
            IntDataMap2D map,
            int localCoordinate
    ) {
        int innerCoordinate =
                localCoordinate
                        - map.innerMin();

        double cellSize =
                cellSizeBlocks(
                        map
                );

        return regionCoordinate
                * (double) MAPREGION_SIZE_BLOCKS
                + innerCoordinate
                * cellSize;
    }

    private double cellSizeBlocks(
            IntDataMap2D map
    ) {
        return MAPREGION_SIZE_BLOCKS
                / (double) map.innerSize();
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

    private record ResourceDataset(
            List<Cell> cells,
            int regionCount,
            int rawMin,
            int rawMax,
            double averageRawValue,
            int distinctValues
    ) {
    }
}