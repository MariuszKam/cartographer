package cartographer.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable coordinate of one render tile in the global render-tile grid.
 *
 * <p>This is a rendering coordinate space. It is intentionally distinct from
 * world-block and source mapchunk coordinates.</p>
 */
public record RenderTileCoordinate(
        int x,
        int z
) {

    /**
     * Returns the square ring at the requested Chebyshev distance.
     *
     * <p>Ordering is deterministic and clockwise: minimum-Z edge from
     * minimum X to maximum X, maximum-X edge toward maximum Z, maximum-Z edge
     * toward minimum X, then minimum-X edge back toward minimum Z.</p>
     */
    public List<RenderTileCoordinate> squareRing(int distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must not be negative");
        }
        if (distance == 0) {
            return List.of(this);
        }

        int minX = checkedCoordinate((long) x - distance);
        int maxX = checkedCoordinate((long) x + distance);
        int minZ = checkedCoordinate((long) z - distance);
        int maxZ = checkedCoordinate((long) z + distance);
        int expectedSize = Math.toIntExact(Math.multiplyExact(8L, distance));
        List<RenderTileCoordinate> ring = new ArrayList<>(expectedSize);

        for (long currentX = minX; currentX <= maxX; currentX++) {
            ring.add(new RenderTileCoordinate((int) currentX, minZ));
        }
        for (long currentZ = (long) minZ + 1; currentZ <= maxZ; currentZ++) {
            ring.add(new RenderTileCoordinate(maxX, (int) currentZ));
        }
        for (long currentX = (long) maxX - 1; currentX >= minX; currentX--) {
            ring.add(new RenderTileCoordinate((int) currentX, maxZ));
        }
        for (long currentZ = (long) maxZ - 1; currentZ > minZ; currentZ--) {
            ring.add(new RenderTileCoordinate(minX, (int) currentZ));
        }

        return List.copyOf(ring);
    }

    private static int checkedCoordinate(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("render-tile ring exceeds coordinate range");
        }
        return (int) value;
    }
}
