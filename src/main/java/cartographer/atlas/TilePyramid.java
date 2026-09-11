package cartographer.atlas;

import cartographer.model.WorldPosition;

import java.util.ArrayList;
import java.util.List;

public class TilePyramid {
    public List<AtlasTile> plan(WorldPosition center, int radiusBlocks, int levels) {
        List<AtlasTile> tiles = new ArrayList<>();
        for (int level = 0; level < levels; level++) {
            int axisTiles = 1 << level;
            int tileRadius = Math.max(1, radiusBlocks / axisTiles);
            int tileDiameter = tileRadius * 2;
            int minX = (int) Math.floor(center.x()) - radiusBlocks;
            int minZ = (int) Math.floor(center.z()) - radiusBlocks;
            for (int z = 0; z < axisTiles; z++) {
                for (int x = 0; x < axisTiles; x++) {
                    double tileCenterX = minX + tileRadius + x * tileDiameter;
                    double tileCenterZ = minZ + tileRadius + z * tileDiameter;
                    tiles.add(new AtlasTile(level, x, z, new WorldPosition(tileCenterX, center.y(), tileCenterZ), tileRadius));
                }
            }
        }
        return List.copyOf(tiles);
    }
}
