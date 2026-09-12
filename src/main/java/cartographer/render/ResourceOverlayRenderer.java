package cartographer.render;

import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.WorldPosition;
import cartographer.resource.ResourceOverlayCell;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ResourceOverlayRenderer {

    private static final int LEGEND_MIN_WIDTH =
            240;

    private static final int LEGEND_MIN_HEIGHT =
            120;

    private final MarkerRenderer markerRenderer =
            new MarkerRenderer();

    public int draw(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            List<ResourceOverlayCell> cells,
            String resourceKey,
            double minimumSignal,
            WorldPosition player,
            HomeState home
    ) {
        if (image == null) {
            throw new IllegalArgumentException(
                    "Image is required"
            );
        }

        if (center == null) {
            throw new IllegalArgumentException(
                    "Map center is required"
            );
        }

        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException(
                    "radiusBlocks must be positive"
            );
        }

        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "Resource key is required"
            );
        }

        Objects.requireNonNull(
                home,
                "Home state is required"
        );

        List<ResourceOverlayCell> safeCells =
                cells == null
                        ? List.of()
                        : cells;

        double scaleX =
                image.getWidth()
                        / (double) (
                        radiusBlocks
                                * 2
                );

        double scaleZ =
                image.getHeight()
                        / (double) (
                        radiusBlocks
                                * 2
                );

        int minWorldX =
                (int) Math.floor(
                        center.x()
                )
                        - radiusBlocks;

        int minWorldZ =
                (int) Math.floor(
                        center.z()
                )
                        - radiusBlocks;

        Graphics2D graphics =
                image.createGraphics();

        int drawn =
                0;

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            for (ResourceOverlayCell cell :
                    safeCells) {

                int startX =
                        (int) Math.floor(
                                (cell.worldMinX()
                                        - minWorldX)
                                        * scaleX
                        );

                int endX =
                        (int) Math.ceil(
                                (cell.worldMaxX()
                                        - minWorldX)
                                        * scaleX
                        );

                int startY =
                        (int) Math.floor(
                                (cell.worldMinZ()
                                        - minWorldZ)
                                        * scaleZ
                        );

                int endY =
                        (int) Math.ceil(
                                (cell.worldMaxZ()
                                        - minWorldZ)
                                        * scaleZ
                        );

                if (endX <= 0
                        || endY <= 0
                        || startX >= image.getWidth()
                        || startY >= image.getHeight()) {

                    continue;
                }

                startX =
                        Math.max(
                                0,
                                startX
                        );

                startY =
                        Math.max(
                                0,
                                startY
                        );

                endX =
                        Math.min(
                                image.getWidth(),
                                endX
                        );

                endY =
                        Math.min(
                                image.getHeight(),
                                endY
                        );

                graphics.setColor(
                        signalColor(
                                cell.relativeIntensity()
                        )
                );

                graphics.fillRect(
                        startX,
                        startY,
                        Math.max(
                                1,
                                endX - startX
                        ),
                        Math.max(
                                1,
                                endY - startY
                        )
                );

                drawn++;
            }

            drawLegend(
                    graphics,
                    image,
                    resourceKey,
                    minimumSignal
            );

            drawMarkers(
                    graphics,
                    image,
                    player,
                    home,
                    minWorldX,
                    minWorldZ,
                    scaleX,
                    scaleZ
            );

        } finally {
            graphics.dispose();
        }

        return drawn;
    }

    private Color signalColor(
            double signal
    ) {
        double clamped =
                Math.clamp(
                        signal,
                        0.0,
                        1.0
                );

        int green =
                (int) Math.round(
                        220.0
                                * (1.0 - clamped)
                );

        int alpha =
                40
                        + (int) Math.round(
                        170.0
                                * clamped
                );

        return new Color(
                255,
                green,
                0,
                alpha
        );
    }

    private void drawLegend(
            Graphics2D graphics,
            BufferedImage image,
            String resourceKey,
            double minimumSignal
    ) {
        if (image.getWidth() < LEGEND_MIN_WIDTH
                || image.getHeight() < LEGEND_MIN_HEIGHT) {

            return;
        }

        int x =
                8;

        int y =
                8;

        int width =
                230;

        int height =
                66;

        graphics.setColor(
                new Color(
                        0,
                        0,
                        0,
                        190
                )
        );

        graphics.fillRect(
                x,
                y,
                width,
                height
        );

        graphics.setColor(
                Color.WHITE
        );

        graphics.drawString(
                "Resource: "
                        + resourceKey,
                x + 8,
                y + 15
        );

        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "Relative signal >= %.2f",
                        minimumSignal
                ),
                x + 8,
                y + 31
        );

        int gradientX =
                x + 8;

        int gradientY =
                y + 42;

        int gradientWidth =
                150;

        int gradientHeight =
                10;

        for (int offset = 0;
             offset < gradientWidth;
             offset++) {

            double signal =
                    offset
                            / (double) (
                            gradientWidth - 1
                    );

            graphics.setColor(
                    signalColor(
                            signal
                    )
            );

            graphics.fillRect(
                    gradientX + offset,
                    gradientY,
                    1,
                    gradientHeight
            );
        }

        graphics.setColor(
                Color.WHITE
        );

        graphics.drawString(
                "low",
                gradientX,
                gradientY + 21
        );

        graphics.drawString(
                "high",
                gradientX
                        + gradientWidth
                        - 25,
                gradientY + 21
        );
    }

    private void drawMarkers(
            Graphics2D graphics,
            BufferedImage image,
            WorldPosition player,
            HomeState home,
            int minWorldX,
            int minWorldZ,
            double scaleX,
            double scaleZ
    ) {
        if (player != null) {
            int playerX =
                    (int) Math.round(
                            (player.x()
                                    - minWorldX)
                                    * scaleX
                    );

            int playerY =
                    (int) Math.round(
                            (player.z()
                                    - minWorldZ)
                                    * scaleZ
                    );

            if (inside(
                    image,
                    playerX,
                    playerY
            )) {
                markerRenderer.drawCross(
                        graphics,
                        playerX,
                        playerY,
                        Color.RED
                );
            }
        }

        if (!(home instanceof HomeState.Present(HomeLocation location))) {
            return;
        }

        int homeX =
                (int) Math.round(
                        (location.x()
                                - minWorldX)
                                * scaleX
                );

        int homeY =
                (int) Math.round(
                        (location.z()
                                - minWorldZ)
                                * scaleZ
                );

        if (inside(
                image,
                homeX,
                homeY
        )) {
            markerRenderer.drawCross(
                    graphics,
                    homeX,
                    homeY,
                    Color.CYAN
            );
        }
    }

    private boolean inside(
            BufferedImage image,
            int x,
            int y
    ) {
        return x >= 0
                && y >= 0
                && x < image.getWidth()
                && y < image.getHeight();
    }
}