package cartographer.render;

import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.WorldPosition;
import cartographer.resource.SurfaceResourceAnalysis;
import cartographer.resource.SurfaceResourceDeposit;
import cartographer.resource.SurfaceResourcePoint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

public class SurfaceResourceOverlayRenderer {

    private static final int MIN_LEGEND_WIDTH =
            240;

    private static final int MIN_LEGEND_HEIGHT =
            120;

    private final MarkerRenderer markerRenderer =
            new MarkerRenderer();

    public int draw(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            SurfaceResourceAnalysis analysis,
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

        if (analysis == null) {
            throw new IllegalArgumentException(
                    "Surface resource analysis is required"
            );
        }

        Objects.requireNonNull(
                home,
                "Home state is required"
        );

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

        int drawnBlocks =
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

            graphics.setColor(
                    new Color(
                            255,
                            0,
                            220,
                            210
                    )
            );

            for (SurfaceResourcePoint point :
                    analysis.matchingBlocks()) {

                int startX =
                        (int) Math.floor(
                                (point.worldX()
                                        - minWorldX)
                                        * scaleX
                        );

                int endX =
                        (int) Math.ceil(
                                (point.worldX()
                                        + 1
                                        - minWorldX)
                                        * scaleX
                        );

                int startY =
                        (int) Math.floor(
                                (point.worldZ()
                                        - minWorldZ)
                                        * scaleZ
                        );

                int endY =
                        (int) Math.ceil(
                                (point.worldZ()
                                        + 1
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

                drawnBlocks++;
            }

            drawDepositCenters(
                    graphics,
                    image,
                    analysis.deposits(),
                    minWorldX,
                    minWorldZ,
                    scaleX,
                    scaleZ
            );

            drawLegend(
                    graphics,
                    image,
                    analysis
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

        return drawnBlocks;
    }

    private void drawDepositCenters(
            Graphics2D graphics,
            BufferedImage image,
            List<SurfaceResourceDeposit> deposits,
            int minWorldX,
            int minWorldZ,
            double scaleX,
            double scaleZ
    ) {
        graphics.setStroke(
                new BasicStroke(
                        2.0f
                )
        );

        int limit =
                Math.min(
                        20,
                        deposits.size()
                );

        for (int index = 0;
             index < limit;
             index++) {

            SurfaceResourceDeposit deposit =
                    deposits.get(
                            index
                    );

            int x =
                    (int) Math.round(
                            (deposit.centerWorldX()
                                    - minWorldX)
                                    * scaleX
                    );

            int y =
                    (int) Math.round(
                            (deposit.centerWorldZ()
                                    - minWorldZ)
                                    * scaleZ
                    );

            if (!inside(
                    image,
                    x,
                    y
            )) {
                continue;
            }

            int radius =
                    deposit.blockCount() >= 100
                            ? 8
                            : deposit.blockCount() >= 25
                            ? 6
                            : 4;

            graphics.setColor(
                    Color.YELLOW
            );

            graphics.drawOval(
                    x - radius,
                    y - radius,
                    radius * 2,
                    radius * 2
            );
        }
    }

    private void drawLegend(
            Graphics2D graphics,
            BufferedImage image,
            SurfaceResourceAnalysis analysis
    ) {
        if (image.getWidth() < MIN_LEGEND_WIDTH
                || image.getHeight() < MIN_LEGEND_HEIGHT) {

            return;
        }

        int x =
                8;

        int y =
                8;

        int width =
                225;

        int height =
                63;

        graphics.setColor(
                new Color(
                        0,
                        0,
                        0,
                        150
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
                "Surface resource: "
                        + analysis.query(),
                x + 8,
                y + 16
        );

        graphics.drawString(
                "Blocks: "
                        + analysis.matchingBlockCount(),
                x + 8,
                y + 32
        );

        graphics.drawString(
                "Deposits: "
                        + analysis.depositCount(),
                x + 8,
                y + 48
        );

        graphics.setColor(
                new Color(
                        255,
                        0,
                        220
                )
        );

        graphics.fillRect(
                x + 155,
                y + 25,
                12,
                12
        );

        graphics.setColor(
                Color.YELLOW
        );

        graphics.drawOval(
                x + 181,
                y + 25,
                12,
                12
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
