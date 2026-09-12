package cartographer.coverage;

import cartographer.model.MapChunk;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RegionCoverageAnalyzer {
    public static final int REGION_SIZE_BLOCKS =
            MapRegionCoordinate.SIZE_MAP_CHUNKS
                    * MapChunk.SIZE;

    public RegionCoverageSummary analyze(
            List<ServerMapRegion> regions,
            WorldMetadata metadata
    ) {
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "World metadata is required"
            );
        }

        Set<MapRegionCoordinate> coordinates =
                new TreeSet<>(
                        Comparator.comparingInt(
                                        MapRegionCoordinate::x
                                )
                                .thenComparingInt(
                                        MapRegionCoordinate::z
                                )
                );

        if (regions != null) {
            for (ServerMapRegion region : regions) {
                if (region != null) {
                    coordinates.add(
                            region.coordinate()
                    );
                }
            }
        }

        if (coordinates.isEmpty()) {
            return RegionCoverageSummary.empty();
        }

        int minRegionX =
                coordinates.stream()
                        .mapToInt(
                                MapRegionCoordinate::x
                        )
                        .min()
                        .orElseThrow();

        int maxRegionX =
                coordinates.stream()
                        .mapToInt(
                                MapRegionCoordinate::x
                        )
                        .max()
                        .orElseThrow();

        int minRegionZ =
                coordinates.stream()
                        .mapToInt(
                                MapRegionCoordinate::z
                        )
                        .min()
                        .orElseThrow();

        int maxRegionZ =
                coordinates.stream()
                        .mapToInt(
                                MapRegionCoordinate::z
                        )
                        .max()
                        .orElseThrow();

        int gridWidth =
                Math.addExact(
                        Math.subtractExact(
                                maxRegionX,
                                minRegionX
                        ),
                        1
                );

        int gridHeight =
                Math.addExact(
                        Math.subtractExact(
                                maxRegionZ,
                                minRegionZ
                        ),
                        1
                );

        int possibleCells =
                Math.multiplyExact(
                        gridWidth,
                        gridHeight
                );

        List<RegionCoverageCell> cells =
                buildCells(
                        coordinates,
                        minRegionX,
                        maxRegionX,
                        minRegionZ,
                        maxRegionZ,
                        metadata
                );

        int presentCells =
                coordinates.size();

        int missingCells =
                possibleCells
                        - presentCells;

        int worldMinX =
                minRegionX
                        * REGION_SIZE_BLOCKS;

        int worldMinZ =
                minRegionZ
                        * REGION_SIZE_BLOCKS;

        int worldMaxXExclusive =
                (maxRegionX + 1)
                        * REGION_SIZE_BLOCKS;

        int worldMaxZExclusive =
                (maxRegionZ + 1)
                        * REGION_SIZE_BLOCKS;

        return new RegionCoverageSummary(
                false,
                cells,
                minRegionX,
                maxRegionX,
                minRegionZ,
                maxRegionZ,
                gridWidth,
                gridHeight,
                possibleCells,
                presentCells,
                missingCells,
                presentCells
                        * 100.0
                        / possibleCells,
                worldMinX,
                worldMinZ,
                worldMaxXExclusive,
                worldMaxZExclusive,
                displayX(
                        worldMinX,
                        metadata
                ),
                displayZ(
                        worldMinZ,
                        metadata
                ),
                displayX(
                        worldMaxXExclusive,
                        metadata
                ),
                displayZ(
                        worldMaxZExclusive,
                        metadata
                )
        );
    }

    private List<RegionCoverageCell> buildCells(
            Set<MapRegionCoordinate> present,
            int minRegionX,
            int maxRegionX,
            int minRegionZ,
            int maxRegionZ,
            WorldMetadata metadata
    ) {
        ArrayList<RegionCoverageCell> cells =
                new ArrayList<>();

        for (int regionZ = minRegionZ;
             regionZ <= maxRegionZ;
             regionZ++) {

            for (int regionX = minRegionX;
                 regionX <= maxRegionX;
                 regionX++) {

                MapRegionCoordinate coordinate =
                        new MapRegionCoordinate(
                                regionX,
                                regionZ
                        );

                int worldMinX =
                        regionX
                                * REGION_SIZE_BLOCKS;

                int worldMinZ =
                        regionZ
                                * REGION_SIZE_BLOCKS;

                int worldMaxXExclusive =
                        worldMinX
                                + REGION_SIZE_BLOCKS;

                int worldMaxZExclusive =
                        worldMinZ
                                + REGION_SIZE_BLOCKS;

                cells.add(
                        new RegionCoverageCell(
                                coordinate,
                                present.contains(
                                        coordinate
                                ),
                                worldMinX,
                                worldMinZ,
                                worldMaxXExclusive,
                                worldMaxZExclusive,
                                displayX(
                                        worldMinX,
                                        metadata
                                ),
                                displayZ(
                                        worldMinZ,
                                        metadata
                                ),
                                displayX(
                                        worldMaxXExclusive,
                                        metadata
                                ),
                                displayZ(
                                        worldMaxZExclusive,
                                        metadata
                                )
                        )
                );
            }
        }

        return List.copyOf(
                cells
        );
    }

    private double displayX(
            int worldX,
            WorldMetadata metadata
    ) {
        return worldX
                - metadata.originX();
    }

    private double displayZ(
            int worldZ,
            WorldMetadata metadata
    ) {
        return worldZ
                - metadata.originZ();
    }
}
