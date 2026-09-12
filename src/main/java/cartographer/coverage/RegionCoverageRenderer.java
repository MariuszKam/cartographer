package cartographer.coverage;

import cartographer.model.HomeLocation;
import cartographer.model.WorldPosition;
import cartographer.render.MarkerRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

    public BufferedImage render(
            RegionCoverageSummary summary,
            WorldPosition player,
            Optional<HomeLocation> home
    ) {
        if (summary == null) {
            throw new IllegalArgumentException(
                    "coverage summary is required"
            );
        }

        if (summary.empty()) {
            return renderEmpty();
        }

        int cellSize =
                cellSize(
                        summary
                );

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

        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_ARGB
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
                    cellSize
            );

            drawMarkers(
                    graphics,
                    summary,
                    cellSize,
                    player,
                    home.isEmpty()
                            ? Optional.empty()
                            : home
            );

            drawLegend(
                    graphics,
                    image,
                    summary
            );

        } finally {
            graphics.dispose();
        }

        return image;
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

        return Math.max(
                MIN_CELL_SIZE,
                Math.min(
                        TARGET_CELL_SIZE,
                        available
                                / Math.max(
                                        1,
                                        largestDimension
                                )
                )
        );
    }

    private void drawCells(
            Graphics2D graphics,
            RegionCoverageSummary summary,
            int cellSize
    ) {
        for (RegionCoverageCell cell : summary.cells()) {
            int x =
                    PADDING
                            + (cell.coordinate().x()
                            - summary.minRegionX())
                            * cellSize;

            int y =
                    PADDING
                            + (cell.coordinate().z()
                            - summary.minRegionZ())
                            * cellSize;

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
                    cellSize,
                    cellSize
            );

            if (cellSize >= 6) {
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
                        cellSize,
                        cellSize
                );
            }
        }
    }

    private void drawMarkers(
            Graphics2D graphics,
            RegionCoverageSummary summary,
            int cellSize,
            WorldPosition player,
            Optional<HomeLocation> home
    ) {
        if (player != null
                && insideWorldBounds(
                summary,
                player.x(),
                player.z()
        )) {
            markerRenderer.drawCross(
                    graphics,
                    imageX(
                            summary,
                            cellSize,
                            player.x()
                    ),
                    imageY(
                            summary,
                            cellSize,
                            player.z()
                    ),
                    Color.RED
            );
        }

        if (home.isEmpty()) {
            return;
        }

        HomeLocation location =
                home.get();

        if (!insideWorldBounds(
                summary,
                location.x(),
                location.z()
        )) {
            return;
        }

        markerRenderer.drawCross(
                graphics,
                imageX(
                        summary,
                        cellSize,
                        location.x()
                ),
                imageY(
                        summary,
                        cellSize,
                        location.z()
                ),
                Color.CYAN
        );
    }

    private boolean insideWorldBounds(
            RegionCoverageSummary summary,
            double worldX,
            double worldZ
    ) {
        return worldX >= summary.worldMinX()
                && worldX < summary.worldMaxXExclusive()
                && worldZ >= summary.worldMinZ()
                && worldZ < summary.worldMaxZExclusive();
    }

    private int imageX(
            RegionCoverageSummary summary,
            int cellSize,
            double worldX
    ) {
        double fraction =
                (worldX - summary.worldMinX())
                        / (summary.worldMaxXExclusive()
                        - (double) summary.worldMinX());

        return PADDING
                + (int) Math.round(
                fraction
                        * summary.gridWidth()
                        * cellSize
        );
    }

    private int imageY(
            RegionCoverageSummary summary,
            int cellSize,
            double worldZ
    ) {
        double fraction =
                (worldZ - summary.worldMinZ())
                        / (summary.worldMaxZExclusive()
                        - (double) summary.worldMinZ());

        return PADDING
                + (int) Math.round(
                fraction
                        * summary.gridHeight()
                        * cellSize
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
