package cartographer.render;

/**
 * Height lookup contract used by the raster renderer.
 *
 * <p>Implementations may retain exact block-resolution viewport state or only
 * the world columns observable by the current raster plus hillshade
 * neighbors.</p>
 */
interface TerrainHeightField {
    boolean hasHeightAt(int worldX, int worldZ);

    int heightAt(int worldX, int worldZ);

    int minHeight();

    int maxHeight();

    int sampleCount();
}
