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
import java.util.stream.Collectors;

public class MapRenderer {
    private static final int MAX_IMAGE_SIZE = 4096;
    private final TerrainPalette palette = new TerrainPalette();
    private final MarkerRenderer markers = new MarkerRenderer();

    public BufferedImage render(WorldPosition player, Optional<HomeLocation> home, List<MapChunk> chunks, int radiusBlocks) {
        return render(player, home, chunks, new RenderOptions(radiusBlocks, 1, RenderStyle.SIMPLE, RenderLayer.defaults()), ProgressReporter.NONE).image();
    }

    public BufferedImage render(WorldPosition player, Optional<HomeLocation> home, List<MapChunk> chunks, int radiusBlocks, ProgressReporter progress) {
        return render(player, home, chunks, new RenderOptions(radiusBlocks, 1, RenderStyle.SIMPLE, RenderLayer.defaults()), progress).image();
    }

    public RenderedMap render(WorldPosition center, Optional<HomeLocation> home, List<MapChunk> chunks, RenderOptions options, ProgressReporter progress) {
        int diameter = Math.max(64, Math.min(MAX_IMAGE_SIZE, options.radiusBlocks() * 2 * options.pixelsPerBlock() + 1));
        double scale = diameter / (double) (options.radiusBlocks() * 2);
        BufferedImage image = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);

        progress.start("Preparing image background");
        for (int y = 0; y < diameter; y++) {
            for (int x = 0; x < diameter; x++) {
                image.setRGB(x, y, palette.background(options.style()));
            }
            progress.progress("Preparing image background", y + 1, diameter);
        }

        int tilesDrawn = 0;
        int minX = (int) Math.floor(center.x()) - options.radiusBlocks();
        int minZ = (int) Math.floor(center.z()) - options.radiusBlocks();
        if (options.layers().contains(RenderLayer.TERRAIN) || options.layers().contains(RenderLayer.WATER)) {
            for (int index = 0; index < chunks.size(); index++) {
                MapChunk chunk = chunks.get(index);
                progress.progress("Drawing mapchunks", index + 1, chunks.size());
                for (MapTile tile : chunk.tiles()) {
                    int imageX = (int) Math.round((tile.worldX() - minX) * scale);
                    int imageY = (int) Math.round((tile.worldZ() - minZ) * scale);
                    if (imageX >= 0 && imageX < diameter && imageY >= 0 && imageY < diameter) {
                        image.setRGB(imageX, imageY, palette.tileColor(tile.argb(), tile.height(), options.style()));
                        tilesDrawn++;
                    }
                }
            }
        }

        int markerCount = 0;
        progress.start("Drawing markers");
        if (options.layers().contains(RenderLayer.MARKERS)) {
            Graphics2D graphics = image.createGraphics();
            try {
                int playerX = (int) Math.round((center.x() - minX) * scale);
                int playerY = (int) Math.round((center.z() - minZ) * scale);
                markers.drawCross(graphics, playerX, playerY, Color.RED);
                markerCount++;

                home.ifPresent(location -> {
                    int homeX = (int) Math.round((location.x() - minX) * scale);
                    int homeY = (int) Math.round((location.z() - minZ) * scale);
                    markers.drawCross(graphics, homeX, homeY, Color.CYAN);
                });
                if (home.isPresent()) {
                    markerCount++;
                }
            } finally {
                graphics.dispose();
            }
        }
        progress.done("Markers drawn");

        String layers = options.layers().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
        return new RenderedMap(image, new MapRenderReport(diameter, diameter, chunks.size(), tilesDrawn, markerCount, options.style(), layers));
    }
}
