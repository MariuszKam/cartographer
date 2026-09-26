package cartographer.render;

import cartographer.cache.TerrainHeightTile;
import cartographer.model.MapChunk;

import java.util.Collection;
import java.util.Objects;

/**
 * Stable height normalization range shared by render tiles that must use the
 * same terrain palette scale.
 */
public record TerrainColorRange(
        int minHeight,
        int maxHeight
) {
    public TerrainColorRange {
        if (maxHeight < minHeight) {
            throw new IllegalArgumentException(
                    "maxHeight must not be lower than minHeight"
            );
        }
    }

    public static TerrainColorRange fromTiles(
            Collection<TerrainHeightTile> tiles
    ) {
        Objects.requireNonNull(tiles, "tiles are required");
        boolean found = false;
        int min = 0;
        int max = 0;

        for (TerrainHeightTile tile : tiles) {
            Objects.requireNonNull(tile, "tiles cannot contain null");
            if (!tile.hasEffectiveHeight()) {
                continue;
            }
            for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
                for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                    int height = tile.effectiveHeightAt(localX, localZ);
                    if (!found) {
                        min = height;
                        max = height;
                        found = true;
                    } else {
                        min = Math.min(min, height);
                        max = Math.max(max, height);
                    }
                }
            }
        }

        return found
                ? new TerrainColorRange(min, max)
                : new TerrainColorRange(0, 0);
    }
}
