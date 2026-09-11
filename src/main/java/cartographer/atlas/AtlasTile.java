package cartographer.atlas;

import cartographer.model.WorldPosition;

public record AtlasTile(int level, int x, int z, WorldPosition center, int radiusBlocks) {
}
