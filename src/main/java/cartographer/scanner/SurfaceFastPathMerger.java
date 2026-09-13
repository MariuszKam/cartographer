package cartographer.scanner;

import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceBlock;
import cartographer.model.WorldMetadata;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SurfaceFastPathMerger {

    private static final Comparator<SurfaceBlock> ORDER =
            Comparator.comparingInt(SurfaceBlock::worldZ)
                    .thenComparingInt(SurfaceBlock::worldX)
                    .thenComparingInt(SurfaceBlock::y);

    public List<SurfaceBlock> merge(
            List<SurfaceBlock> fastBlocks,
            List<SurfaceBlock> fallbackBlocks,
            Collection<MapChunkCoordinate> fallbackMapChunks,
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius
    ) {
        Objects.requireNonNull(fastBlocks, "fastBlocks is required");
        Objects.requireNonNull(fallbackBlocks, "fallbackBlocks is required");
        Objects.requireNonNull(fallbackMapChunks, "fallbackMapChunks is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }

        Set<MapChunkCoordinate> fallbackSet = new HashSet<>();
        for (MapChunkCoordinate coordinate : fallbackMapChunks) {
            fallbackSet.add(
                    Objects.requireNonNull(
                            coordinate,
                            "fallbackMapChunks cannot contain null"
                    )
            );
        }

        Map<WorldColumn, SurfaceBlock> selected = new HashMap<>();
        Set<WorldColumn> fallbackColumns = new HashSet<>();
        for (SurfaceBlock block : fastBlocks) {
            addIfEligible(
                    selected,
                    block,
                    fallbackSet,
                    metadata,
                    centerWorldX,
                    centerWorldZ,
                    radius,
                    false,
                    fallbackColumns
            );
        }

        for (SurfaceBlock block : fallbackBlocks) {
            addIfEligible(
                    selected,
                    block,
                    fallbackSet,
                    metadata,
                    centerWorldX,
                    centerWorldZ,
                    radius,
                    true,
                    fallbackColumns
            );
        }

        return selected.values().stream()
                .sorted(ORDER)
                .toList();
    }

    private void addIfEligible(
            Map<WorldColumn, SurfaceBlock> selected,
            SurfaceBlock block,
            Set<MapChunkCoordinate> fallbackMapChunks,
            WorldMetadata metadata,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            boolean fallback,
            Set<WorldColumn> fallbackColumns
    ) {
        Objects.requireNonNull(block, "surface blocks cannot contain null");
        if (!withinWorld(block, metadata)
                || !SurfaceBlockCoordinates.withinCircle(
                block,
                centerWorldX,
                centerWorldZ,
                radius
        )) {
            return;
        }

        MapChunkCoordinate mapChunk =
                SurfaceBlockCoordinates.mapChunkOf(block);
        if (!fallback && fallbackMapChunks.contains(mapChunk)) {
            return;
        }

        WorldColumn column = new WorldColumn(block.worldX(), block.worldZ());
        SurfaceBlock current = selected.get(column);
        if (current == null
                || fallback && !fallbackColumns.contains(column)
                || block.y() > current.y()) {
            selected.put(column, block);
            if (fallback) {
                fallbackColumns.add(column);
            }
        }
    }

    private boolean withinWorld(SurfaceBlock block, WorldMetadata metadata) {
        return block.worldX() >= 0
                && block.worldX() < metadata.mapSizeX()
                && block.worldZ() >= 0
                && block.worldZ() < metadata.mapSizeZ();
    }

    private record WorldColumn(int worldX, int worldZ) {
    }
}
