package cartographer.coverage;

import cartographer.model.WorldPosition;
import cartographer.model.HomeState;
import cartographer.model.WorldPosition;
import cartographer.render.MarkerRenderer;
import cartographer.render.MapViewportGeometry;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;

public class RegionCoverageRenderer {

    private static final int TARGET_CELL_SIZE =
            14;

    private static final int MIN_CELL_SIZE =
            2;

    private static final int MAX_IMAGE_SIZE =
            2048;

    private static final int PADDING =
            20;

    private static final int LEGEND_HEIGHT =
            54;

    private static final int EMPTY_IMAGE_SIZE =
            160;

    private final MarkerRenderer markerRenderer =
            new MarkerRenderer();

    public RegionCoverageRenderResult render(
            RegionCoverageSummary summary,
            WorldPosition player,
            HomeState home
    ) {
        if (summary == null) {
            throw new IllegalArgumentException(
                    "coverage summary is required"
            );
        }

        Objects.requireNonNull(
                home,
                "Home state is required"
        );

        if (summary.empty()) {
            return new RegionCoverageRenderResult(renderEmpty(), Optional.empty());
        }

        int cellSize =
                cellSize(
                        summary
                );

        BufferedImage image =
                getImage(
                        summary,
                        cellSize
                );

        int contentWidth = image.getWidth() - PADDING * 2;
        int contentHeight = image.getHeight() - PADDING * 2 - LEGEND_HEIGHT;
        MapViewportGeometry geometry = new MapViewportGeometry(
                image.getWidth(),
                image.getHeight(),
                PADDING,
                PADDING,
                contentWidth,
                contentHeight,
                summary.worldMinX(),
                summary.worldMinZ(),
                summary.worldMaxXExclusive(),
                summary.worldMaxZExclusive()
        );

        Graphics2D graphics =
                image.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            fill(
                    graphics,
                    image,
                    new Color(
                            22,
                            25,
                            28
                    )
            );

            drawCells(
                    graphics,
                    summary,
                    geometry
            );

            drawMarkers(
                    graphics,
                    geometry,
                    player,
                    home
            );

            drawLegend(
                    graphics,
                    image,
                    summary
            );

        } finally {
            graphics.dispose();
        }

        return new RegionCoverageRenderResult(image, Optional.of(geometry));
    }

    private static BufferedImage getImage(
            RegionCoverageSummary summary,
            int cellSize
    ) {
        int mapWidth =
                summary.gridWidth()
                        * cellSize;

        int mapHeight =
                summary.gridHeight()
                        * cellSize;

        int width =
                Math.min(
                        MAX_IMAGE_SIZE,
                        mapWidth
                                + PADDING
                                * 2
                );

        int height =
                Math.min(
                        MAX_IMAGE_SIZE,
                        mapHeight
                                + PADDING
                                * 2
                                + LEGEND_HEIGHT
                );

        return new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );
    }

    private BufferedImage renderEmpty() {
        BufferedImage image =
                new BufferedImage(
                        EMPTY_IMAGE_SIZE,
                        EMPTY_IMAGE_SIZE,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D graphics =
                image.createGraphics();

        try {
            fill(
                    graphics,
                    image,
                    new Color(
                            22,
                            25,
                            28
                    )
            );

            graphics.setColor(
                    Color.WHITE
            );

            graphics.drawString(
                    "No mapregions",
                    24,
                    80
            );

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private int cellSize(
            RegionCoverageSummary summary
    ) {
        int largestDimension =
                Math.max(
                        summary.gridWidth(),
                        summary.gridHeight()
                );

        int available =
                MAX_IMAGE_SIZE
                        - PADDING
                        * 2
                        - LEGEND_HEIGHT;

        return Math.clamp(
                available
                        / Math.max(
                        1,
                        largestDimension
                ),
                MIN_CELL_SIZE,
                TARGET_CELL_SIZE
        );
    }

    private void drawCells(
            Graphics2D graphics,
            RegionCoverageSummary summary,
            MapViewportGeometry geometry
    ) {
        for (RegionCoverageCell cell :
                summary.cells()) {

            int startX = (int) Math.floor(
                    geometry.absoluteWorldXToImageX(cell.worldMinX())
            );
            int startY = (int) Math.floor(
                    geometry.absoluteWorldZToImageY(cell.worldMinZ())
            );
            int endX = (int) Math.ceil(
                    geometry.absoluteWorldXToImageX(cell.worldMaxXExclusive())
            );
            int endY = (int) Math.ceil(
                    geometry.absoluteWorldZToImageY(cell.worldMaxZExclusive())
            );

            int x = Math.max(geometry.contentX(), startX);
            int y = Math.max(geometry.contentY(), startY);
            int right = Math.min(
                    geometry.contentX() + geometry.contentWidth(), endX
            );
            int bottom = Math.min(
                    geometry.contentY() + geometry.contentHeight(), endY
            );

            if (right <= x || bottom <= y) {
                continue;
            }

            graphics.setColor(
                    cell.present()
                            ? new Color(
                            76,
                            168,
                            108
                    )
                            : new Color(
                            58,
                            63,
                            68
                    )
            );

            graphics.fillRect(
                    x,
                    y,
                    right - x,
                    bottom - y
            );

            if (right - x >= 6 && bottom - y >= 6) {
                graphics.setColor(
                        new Color(
                                15,
                                17,
                                19,
                                140
                        )
                );

                graphics.drawRect(
                        x,
                        y,
                        right - x,
                        bottom - y
                );
            }
        }
    }

    private void drawMarkers(
            Graphics2D graphics,
            MapViewportGeometry geometry,
            WorldPosition player,
            HomeState home
    ) {
        if (player != null
                && geometry.containsAbsoluteWorldPoint(
                player.x(),
                player.z()
        )) {

            markerRenderer.drawCross(
                    graphics,
                    (int) Math.round(geometry.absoluteWorldXToImageX(player.x())),
                    (int) Math.round(geometry.absoluteWorldZToImageY(player.z())),
                    Color.RED
            );
        }

        if (!(home instanceof HomeState.Present(WorldPosition location))) {
            return;
        }

        if (!geometry.containsAbsoluteWorldPoint(
                location.x(),
                location.z()
        )) {
            return;
        }

        markerRenderer.drawCross(
                graphics,
                (int) Math.round(geometry.absoluteWorldXToImageX(location.x())),
                (int) Math.round(geometry.absoluteWorldZToImageY(location.z())),
                Color.CYAN
        );
    }

    private void drawLegend(
            Graphics2D graphics,
            BufferedImage image,
            RegionCoverageSummary summary
    ) {
        int y =
                image.getHeight()
                        - LEGEND_HEIGHT
                        + 10;

        graphics.setColor(
                Color.WHITE
        );

        graphics.drawString(
                "Saved mapregion coverage",
                PADDING,
                y
        );

        graphics.setColor(
                new Color(
                        76,
                        168,
                        108
                )
        );

        graphics.fillRect(
                PADDING,
                y + 12,
                12,
                12
        );

        graphics.setColor(
                Color.WHITE
        );

        graphics.drawString(
                "available",
                PADDING + 18,
                y + 23
        );

        graphics.setColor(
                new Color(
                        58,
                        63,
                        68
                )
        );

        graphics.fillRect(
                PADDING + 98,
                y + 12,
                12,
                12
        );

        graphics.setColor(
                Color.WHITE
        );

        graphics.drawString(
                "missing inside bounds",
                PADDING + 116,
                y + 23
        );

        graphics.drawString(
                summary.presentCells()
                        + "/"
                        + summary.possibleCells()
                        + " regions",
                PADDING,
                y + 42
        );
    }

    private void fill(
            Graphics2D graphics,
            BufferedImage image,
            Color color
    ) {
        graphics.setColor(
                color
        );

        graphics.fillRect(
                0,
                0,
                image.getWidth(),
                image.getHeight()
        );
    }
}
