package cartographer.render;

import cartographer.application.ActualOreOverlayResult;
import cartographer.application.ActualOreOverlaySpec;
import cartographer.model.WorldPosition;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapCell;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

public class ActualOreOverlayPainter {

    private static final Color LABEL_BACKGROUND = new Color(0, 0, 0, 175);
    private static final Color LABEL_TEXT = new Color(255, 236, 204);

    public void paint(
            BufferedImage image,
            ActualBlockMap map,
            WorldPosition center,
            int radiusBlocks
    ) {
        paint(
                image,
                List.of(
                        new ActualOreOverlayResult(
                                new ActualOreOverlaySpec(
                                        map.match(),
                                        map.match(),
                                        new Color(225, 92, 24)
                                ),
                                map
                        )
                ),
                center,
                radiusBlocks
        );
    }

    public void paint(
            BufferedImage image,
            List<ActualOreOverlayResult> overlays,
            WorldPosition center,
            int radiusBlocks
    ) {
        Objects.requireNonNull(image, "image is required");
        Objects.requireNonNull(overlays, "overlays are required");
        Objects.requireNonNull(center, "center is required");
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("radiusBlocks must be positive");
        }

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF
            );
            for (ActualOreOverlayResult overlay : overlays) {
                drawCells(
                        graphics,
                        image,
                        overlay.map(),
                        overlay.spec().color(),
                        center,
                        radiusBlocks
                );
            }
            drawLegend(graphics, image, overlays);
        } finally {
            graphics.dispose();
        }
    }

    private void drawCells(
            Graphics2D graphics,
            BufferedImage image,
            ActualBlockMap map,
            Color baseColor,
            WorldPosition center,
            int radiusBlocks
    ) {
        int minWorldX = (int) Math.floor(center.x()) - radiusBlocks;
        int minWorldZ = (int) Math.floor(center.z()) - radiusBlocks;
        double scaleX = image.getWidth() / (double) (radiusBlocks * 2);
        double scaleZ = image.getHeight() / (double) (radiusBlocks * 2);

        for (ActualBlockMapCell cell : map.cells()) {
            int startX = (int) Math.floor((cell.worldX() - minWorldX) * scaleX);
            int endX = (int) Math.ceil((cell.worldX() + 1 - minWorldX) * scaleX);
            int startY = (int) Math.floor((cell.worldZ() - minWorldZ) * scaleZ);
            int endY = (int) Math.ceil((cell.worldZ() + 1 - minWorldZ) * scaleZ);

            if (endX <= 0 || endY <= 0
                    || startX >= image.getWidth()
                    || startY >= image.getHeight()) {
                continue;
            }

            startX = Math.max(0, startX);
            startY = Math.max(0, startY);
            endX = Math.clamp(endX, startX + 1, image.getWidth());
            endY = Math.clamp(endY, startY + 1, image.getHeight());

            double density = Math.min(
                    1.0,
                    Math.log(cell.matchCount() + 1.0) / Math.log(10.0)
            );
            graphics.setColor(new Color(
                    baseColor.getRed(),
                    baseColor.getGreen(),
                    baseColor.getBlue(),
                    (int) Math.round(85 + 110 * density)
            ));
            graphics.fillRect(startX, startY, endX - startX, endY - startY);
        }
    }

    private void drawLegend(
            Graphics2D graphics,
            BufferedImage image,
            List<ActualOreOverlayResult> overlays
    ) {
        if (overlays.isEmpty() || image.getWidth() < 120 || image.getHeight() < 40) {
            return;
        }

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        graphics.setFont(graphics.getFont().deriveFont(Font.PLAIN, 11.0f));

        int rowHeight = 18;
        int boxHeight = 28 + overlays.size() * rowHeight;
        int boxWidth = Math.min(image.getWidth() - 8, 360);
        if (boxWidth <= 0) {
            return;
        }

        graphics.setColor(LABEL_BACKGROUND);
        graphics.fillRect(4, 4, boxWidth, Math.min(boxHeight, image.getHeight() - 8));
        graphics.setColor(LABEL_TEXT);
        graphics.drawString("ACTUAL ORE", 10, 17);

        int y = 34;
        for (ActualOreOverlayResult overlay : overlays) {
            if (y > image.getHeight() - 5) {
                break;
            }
            graphics.setColor(overlay.spec().color());
            graphics.fillRect(10, y - 10, 10, 10);
            graphics.setColor(LABEL_TEXT);
            graphics.drawString(
                    overlay.spec().displayName()
                            + "  "
                            + overlay.map().matchingBlocks()
                            + " blocks",
                    26,
                    y
            );
            y += rowHeight;
        }

        if (y <= image.getHeight() - 5) {
            graphics.setColor(LABEL_TEXT);
            graphics.drawString(
                    "Y filter: " + overlays.getFirst().map().yFilter().description(),
                    10,
                    y
            );
        }
    }
}
