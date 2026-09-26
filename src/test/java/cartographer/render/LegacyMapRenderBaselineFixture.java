package cartographer.render;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldPosition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class LegacyMapRenderBaselineFixture {
    static final int RADIUS_BLOCKS = 256;
    static final int EXPECTED_IMAGE_SIZE = 513;
    static final int EXPECTED_CHUNKS = 256;
    static final int EXPECTED_TILES_DRAWN = 263_169;
    static final long EXPECTED_IMAGE_FINGERPRINT = 0x22a97e92ba808c50L;

    private LegacyMapRenderBaselineFixture() {
    }

    static WorldPosition center() {
        return new WorldPosition(0.0, 0.0, 0.0);
    }

    static RenderOptions options() {
        return new RenderOptions(
                RADIUS_BLOCKS,
                1,
                RenderStyle.TOPOGRAPHIC,
                Set.of(RenderLayer.TERRAIN)
        );
    }

    static List<MapChunk> chunks() {
        int radiusInMapChunks = RADIUS_BLOCKS / MapChunk.SIZE;
        int diameterInMapChunks = radiusInMapChunks * 2;
        List<MapChunk> chunks = new ArrayList<>(
                diameterInMapChunks * diameterInMapChunks
        );
        for (int chunkZ = -radiusInMapChunks;
             chunkZ < radiusInMapChunks;
             chunkZ++) {
            for (int chunkX = -radiusInMapChunks;
                 chunkX < radiusInMapChunks;
                 chunkX++) {
                chunks.add(chunk(chunkX, chunkZ));
            }
        }
        return List.copyOf(chunks);
    }

    static long fingerprint(BufferedImage image) {
        long fingerprint = 0xcbf29ce484222325L;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                fingerprint ^= Integer.toUnsignedLong(image.getRGB(x, y));
                fingerprint *= 0x100000001b3L;
            }
        }
        return fingerprint;
    }

    private static MapChunk chunk(int chunkX, int chunkZ) {
        int[] heights = new int[MapChunk.HEIGHT_VALUE_COUNT];
        for (int localZ = 0; localZ < MapChunk.SIZE; localZ++) {
            for (int localX = 0; localX < MapChunk.SIZE; localX++) {
                int worldX = chunkX * MapChunk.SIZE + localX;
                int worldZ = chunkZ * MapChunk.SIZE + localZ;
                heights[localZ * MapChunk.SIZE + localX] = 96 + Math.floorMod(
                        worldX * 7 + worldZ * 11 + chunkX * 13 - chunkZ * 5,
                        80
                );
            }
        }
        return new MapChunk(
                new MapChunkCoordinate(chunkX, chunkZ),
                heights,
                new int[0]
        );
    }
}
