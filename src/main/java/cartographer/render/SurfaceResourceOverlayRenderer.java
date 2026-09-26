package cartographer.render;

import cartographer.model.HomeState;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceMaterialDeposit;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceObjectSelectionAnalysis;
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
    private final SurfaceObjectMarkerStylePolicy objectMarkerStyles =
            new SurfaceObjectMarkerStylePolicy();

    public int drawMaterial(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            SurfaceMaterialAnalysis analysis,
            WorldPosition player,
            HomeState home
    ) {
        return drawMaterial(image, center, radiusBlocks, analysis, player, home, true);
    }

    public int drawMaterial(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            SurfaceMaterialAnalysis analysis,
            WorldPosition player,
            HomeState home,
            boolean drawSystemMarkers
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
                    "Surface material analysis is required"
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

            if (drawSystemMarkers) {
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
            }

        } finally {
            graphics.dispose();
        }

        return drawnBlocks;
    }

    public int drawObjects(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            SurfaceObjectSelectionAnalysis analysis,
            WorldPosition player,
            HomeState home,
            boolean drawSystemMarkers
    ) {
        Objects.requireNonNull(analysis, "Surface object selection analysis is required");
        Objects.requireNonNull(image, "Image is required");
        Objects.requireNonNull(center, "Map center is required");
        Objects.requireNonNull(home, "Home state is required");
        double scaleX = image.getWidth() / (double) (radiusBlocks * 2);
        double scaleZ = image.getHeight() / (double) (radiusBlocks * 2);
        int minWorldX = (int) Math.floor(center.x()) - radiusBlocks;
        int minWorldZ = (int) Math.floor(center.z()) - radiusBlocks;
        Graphics2D graphics = image.createGraphics();
        int drawn = 0;
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (SurfaceObjectAnalysis resource : analysis.resources()) {
                SurfaceObjectMarkerStyle style = objectMarkerStyles.forFamilies(resource.families());
                graphics.setColor(SurfaceObjectColorPolicy.colorFor(resource.qualifiedResourceKey()));
                for (SurfaceResourcePoint point : resource.occurrences()) {
                    int startX = (int) Math.floor((point.worldX() - minWorldX) * scaleX);
                    int endX = (int) Math.ceil((point.worldX() + 1 - minWorldX) * scaleX);
                    int startY = (int) Math.floor((point.worldZ() - minWorldZ) * scaleZ);
                    int endY = (int) Math.ceil((point.worldZ() + 1 - minWorldZ) * scaleZ);
                    if (endX <= 0 || endY <= 0 || startX >= image.getWidth()
                            || startY >= image.getHeight()) continue;
                    int markerX = (int) Math.round((point.worldX() + 0.5 - minWorldX) * scaleX);
                    int markerY = (int) Math.round((point.worldZ() + 0.5 - minWorldZ) * scaleZ);
                    drawObjectMarker(graphics, markerX, markerY, style);
                    drawn++;
                }
            }
            drawObjectLegend(graphics, image, analysis);
            if (drawSystemMarkers) {
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
            }
        } finally {
            graphics.dispose();
        }
        return drawn;
    }

    private void drawObjectLegend(
            Graphics2D graphics,
            BufferedImage image,
            SurfaceObjectSelectionAnalysis analysis
    ) {
        if (image.getWidth() < MIN_LEGEND_WIDTH || image.getHeight() < MIN_LEGEND_HEIGHT) return;
        int x = 8;
        int y = 8;
        int entryHeight = 32;
        int availableHeight = image.getHeight() - y - 8;
        int maxEntries = Math.max(1, (availableHeight - 24) / entryHeight);
        boolean overflow = analysis.resources().size() > maxEntries;
        if (overflow) {
            maxEntries = Math.max(1, (availableHeight - 24 - 16) / entryHeight);
        }
        int visibleEntries = Math.min(analysis.resources().size(), maxEntries);
        int height = 24 + visibleEntries * entryHeight + (overflow ? 16 : 0);
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(x, y, 225, height);
        graphics.setColor(Color.WHITE);
        graphics.drawString("Surface objects: " + analysis.resourceCount(), x + 8, y + 16);
        for (int index = 0; index < visibleEntries; index++) {
            SurfaceObjectAnalysis resource = analysis.resources().get(index);
            int rowY = y + 32 + index * entryHeight;
            SurfaceObjectMarkerStyle style = objectMarkerStyles.forFamilies(resource.families());
            graphics.setColor(SurfaceObjectColorPolicy.colorFor(resource.qualifiedResourceKey()));
            drawObjectMarker(graphics, x + 14, rowY - 4, style);
            graphics.setColor(Color.WHITE);
            graphics.drawString(resource.displayName(), x + 26, rowY);
            graphics.drawString(SurfaceObjectPresentation.familyMetricLabel(resource.families()) + ": "
                    + SurfaceObjectPresentation.analysisFamilyText(resource), x + 8, rowY + 12);
            graphics.drawString(resource.occurrenceCount() + " occurrences • "
                    + resource.registryVariantCount() + " variants", x + 8, rowY + 24);
        }
        if (visibleEntries < analysis.resources().size()) {
            graphics.drawString("+" + (analysis.resources().size() - visibleEntries) + " more selected resources",
                    x + 8, y + height - 4);
        }
    }

    private void drawObjectMarker(
            Graphics2D graphics,
            int centerX,
            int centerY,
            SurfaceObjectMarkerStyle style
    ) {
        int radius = style.radius();
        switch (style.shape()) {
            case DIAMOND -> graphics.fillPolygon(
                    new int[]{centerX, centerX + radius, centerX, centerX - radius},
                    new int[]{centerY - radius, centerY, centerY + radius, centerY}, 4);
            case TRIANGLE -> graphics.fillPolygon(
                    new int[]{centerX, centerX + radius, centerX - radius},
                    new int[]{centerY - radius, centerY + radius, centerY + radius}, 3);
            case CIRCLE -> graphics.fillOval(
                    centerX - radius, centerY - radius, radius * 2 + 1, radius * 2 + 1);
            case SQUARE -> graphics.fillRect(
                    centerX - radius, centerY - radius, radius * 2 + 1, radius * 2 + 1);
            case MIXED -> {
                graphics.fillRect(centerX - radius, centerY - 1, radius * 2 + 1, 3);
                graphics.fillRect(centerX - 1, centerY - radius, 3, radius * 2 + 1);
            }
        }
    }

    private void drawDepositCenters(
            Graphics2D graphics,
            BufferedImage image,
            List<SurfaceMaterialDeposit> deposits,
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

            SurfaceMaterialDeposit deposit =
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
            SurfaceMaterialAnalysis analysis
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

        SurfaceOverlayLegend legend = SurfaceOverlayLegend.forAnalysis(analysis);
        graphics.drawString(legend.title(), x + 8, y + 16);
        graphics.drawString(legend.metrics().get(0), x + 8, y + 32);
        graphics.drawString(legend.metrics().get(1), x + 8, y + 48);

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

        if (!(home instanceof HomeState.Present(WorldPosition location))) {
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
