package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.HomeLocation;
import cartographer.model.MapChunk;
import cartographer.model.MapTile;
import cartographer.model.WorldPosition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

public class MapRenderer {
    private static final int MAX_IMAGE_SIZE = 4096;
    private final TerrainPalette palette = new TerrainPalette();
    private final MarkerRenderer markers = new MarkerRenderer();

    public BufferedImage render(WorldPosition player, Optional<HomeLocation> home, List<MapChunk> chunks, int radiusBlocks) {
        return render(player, home, chunks, radiusBlocks, ProgressReporter.NONE);
    }

    public BufferedImage render(WorldPosition player, Optional<HomeLocation> home, List<MapChunk> chunks, int radiusBlocks, ProgressReporter progress) {
        int diameter = Math.max(64, Math.min(MAX_IMAGE_SIZE, radiusBlocks * 2 + 1));
        double scale = diameter / (double) (radiusBlocks * 2);
        BufferedImage image = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);

        progress.start("Preparing image background");
        for (int y = 0; y < diameter; y++) {
            for (int x = 0; x < diameter; x++) {
                image.setRGB(x, y, palette.background());
            }
            progress.progress("Preparing image background", y + 1, diameter);
        }

        int minX = (int) Math.floor(player.x()) - radiusBlocks;
        int minZ = (int) Math.floor(player.z()) - radiusBlocks;
        for (int index = 0; index < chunks.size(); index++) {
            MapChunk chunk = chunks.get(index);
            progress.progress("Drawing mapchunks", index + 1, chunks.size());
            for (MapTile tile : chunk.tiles()) {
                int imageX = (int) Math.round((tile.worldX() - minX) * scale);
                int imageY = (int) Math.round((tile.worldZ() - minZ) * scale);
                if (imageX >= 0 && imageX < diameter && imageY >= 0 && imageY < diameter) {
                    image.setRGB(imageX, imageY, tile.argb());
                }
            }
        }

        progress.start("Drawing markers");
        Graphics2D graphics = image.createGraphics();
        try {
            int playerX = (int) Math.round((player.x() - minX) * scale);
            int playerY = (int) Math.round((player.z() - minZ) * scale);
            markers.drawCross(graphics, playerX, playerY, Color.RED);

            home.ifPresent(location -> {
                int homeX = (int) Math.round((location.x() - minX) * scale);
                int homeY = (int) Math.round((location.z() - minZ) * scale);
                markers.drawCross(graphics, homeX, homeY, Color.CYAN);
            });
        } finally {
            graphics.dispose();
        }
        progress.done("Markers drawn");

        return image;
    }
}
