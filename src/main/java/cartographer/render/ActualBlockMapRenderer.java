package cartographer.render;

import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapCell;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ActualBlockMapRenderer {

    private static final int LEFT_MARGIN =
            56;

    private static final int RIGHT_MARGIN =
            20;

    private static final int TOP_MARGIN =
            24;

    private static final int BOTTOM_MARGIN =
            124;

    private static final int GRID_STEP_BLOCKS =
            64;

    private static final int MAX_IMAGE_DIMENSION =
            8192;

    private static final int DEFAULT_EMPTY_WIDTH =
            420;

    private static final int DEFAULT_EMPTY_HEIGHT =
            120;

    private static final Color BACKGROUND =
            new Color(
                    17,
                    19,
                    22
            );

    private static final Color GRID =
            new Color(
                    255,
                    255,
                    255,
                    36
            );

    private static final Color TEXT =
            new Color(
                    225,
                    229,
                    234
            );

    private static final Color FRAME =
            new Color(
                    220,
                    225,
                    230,
                    100
            );

    private static final Color PLAYER_LINE =
            new Color(
                    74,
                    192,
                    255,
                    180
            );

    private static final Color PLAYER_DOT =
            new Color(
                    180,
                    231,
                    255
            );

    public BufferedImage render(
            ActualBlockMap map,
            int scale
    ) {
        Objects.requireNonNull(
                map,
                "map is required"
        );

        if (scale <= 0) {
            throw new IllegalArgumentException(
                    "scale must be positive"
            );
        }

        if (map.cells().isEmpty()) {
            return renderEmpty(
                    map
            );
        }

        int mapWidth =
                map.diameterBlocks()
                        * scale;

        int mapHeight =
                map.diameterBlocks()
                        * scale;

        int width =
                LEFT_MARGIN
                        + mapWidth
                        + RIGHT_MARGIN;

        int height =
                TOP_MARGIN
                        + mapHeight
                        + BOTTOM_MARGIN;

        validateSize(
                width,
                height
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
            configure(
                    graphics
            );

            fillBackground(
                    graphics,
                    image
            );

            drawGrid(
                    graphics,
                    map,
                    scale,
                    mapWidth,
                    mapHeight
            );

            drawCells(
                    graphics,
                    map,
                    scale
            );

            drawPlayerMarker(
                    graphics,
                    map,
                    scale,
                    mapHeight
            );

            drawFrame(
                    graphics,
                    mapWidth,
                    mapHeight
            );

            drawLabels(
                    graphics,
                    map,
                    mapHeight
            );

            drawLegend(
                    graphics,
                    map,
                    mapHeight
            );

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private BufferedImage renderEmpty(
            ActualBlockMap map
    ) {
        BufferedImage image =
                new BufferedImage(
                        DEFAULT_EMPTY_WIDTH,
                        DEFAULT_EMPTY_HEIGHT,
                        BufferedImage.TYPE_INT_ARGB
                );

        Graphics2D graphics =
                image.createGraphics();

        try {
            configure(
                    graphics
            );

            fillBackground(
                    graphics,
                    image
            );

            graphics.setColor(
                    TEXT
            );

            graphics.drawString(
                    "No actual matching blocks found",
                    20,
                    40
            );

            graphics.drawString(
                    "Match: "
                            + map.match(),
                    20,
                    62
            );

            graphics.drawString(
                    "Center: "
                            + map.centerWorldX()
                            + ","
                            + map.centerWorldZ()
                            + " radius="
                            + map.radius(),
                    20,
                    84
            );

            graphics.drawString(
                    "Filter Y "
                            + map.yFilter()
                            .description(),
                    20,
                    106
            );

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private void configure(
            Graphics2D graphics
    ) {
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        graphics.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        11
                )
        );
    }

    private void fillBackground(
            Graphics2D graphics,
            BufferedImage image
    ) {
        graphics.setColor(
                BACKGROUND
        );

        graphics.fillRect(
                0,
                0,
                image.getWidth(),
                image.getHeight()
        );
    }

    private void drawGrid(
            Graphics2D graphics,
            ActualBlockMap map,
            int scale,
            int mapWidth,
            int mapHeight
    ) {
        graphics.setColor(
                GRID
        );

        for (int offset = 0;
             offset < map.diameterBlocks();
             offset += GRID_STEP_BLOCKS) {

            int x =
                    LEFT_MARGIN
                            + offset
                            * scale;

            graphics.drawLine(
                    x,
                    TOP_MARGIN,
                    x,
                    TOP_MARGIN + mapHeight
            );

            int zY =
                    TOP_MARGIN
                            + offset
                            * scale;

            graphics.drawLine(
                    LEFT_MARGIN,
                    zY,
                    LEFT_MARGIN + mapWidth,
                    zY
            );
        }

        graphics.setColor(
                TEXT
        );

        graphics.drawString(
                "N",
                LEFT_MARGIN + mapWidth / 2,
                14
        );

        graphics.drawString(
                "W",
                10,
                TOP_MARGIN + mapHeight / 2
        );

        graphics.drawString(
                "E",
                LEFT_MARGIN + mapWidth + 6,
                TOP_MARGIN + mapHeight / 2
        );

        graphics.drawString(
                "S",
                LEFT_MARGIN + mapWidth / 2,
                TOP_MARGIN + mapHeight + 16
        );
    }

    private void drawCells(
            Graphics2D graphics,
            ActualBlockMap map,
            int scale
    ) {
        for (ActualBlockMapCell cell :
                map.cells()) {

            int relativeX =
                    cell.worldX()
                            - (map.centerWorldX()
                            - map.radius());

            int relativeZ =
                    (map.centerWorldZ()
                            + map.radius())
                            - cell.worldZ();

            int imageX =
                    LEFT_MARGIN
                            + relativeX
                            * scale;

            int imageY =
                    TOP_MARGIN
                            + relativeZ
                            * scale;

            graphics.setColor(
                    colorFor(
                            cell,
                            map
                    )
            );

            graphics.fillRect(
                    imageX,
                    imageY,
                    scale,
                    scale
            );
        }
    }

    private void drawPlayerMarker(
            Graphics2D graphics,
            ActualBlockMap map,
            int scale,
            int mapHeight
    ) {
        int centerIndex =
                map.radius();

        int x =
                LEFT_MARGIN
                        + centerIndex
                        * scale
                        + Math.max(
                        0,
                        scale / 2
                );

        int y =
                TOP_MARGIN
                        + centerIndex
                        * scale
                        + Math.max(
                        0,
                        scale / 2
                );

        graphics.setColor(
                PLAYER_LINE
        );

        graphics.drawLine(
                x,
                TOP_MARGIN,
                x,
                TOP_MARGIN + mapHeight
        );

        graphics.drawLine(
                LEFT_MARGIN,
                y,
                LEFT_MARGIN + map.diameterBlocks() * scale,
                y
        );

        graphics.setColor(
                PLAYER_DOT
        );

        graphics.fillOval(
                x - 4,
                y - 4,
                8,
                8
        );

        graphics.drawString(
                "PLAYER",
                x + 8,
                Math.max(
                        14,
                        y - 8
                )
        );
    }

    private void drawFrame(
            Graphics2D graphics,
            int mapWidth,
            int mapHeight
    ) {
        graphics.setColor(
                FRAME
        );

        graphics.drawRect(
                LEFT_MARGIN,
                TOP_MARGIN,
                mapWidth,
                mapHeight
        );
    }

    private void drawLabels(
            Graphics2D graphics,
            ActualBlockMap map,
            int mapHeight
    ) {
        graphics.setColor(
                TEXT
        );

        graphics.drawString(
                "Actual block map: "
                        + map.match(),
                LEFT_MARGIN,
                16
        );

        int bottom =
                TOP_MARGIN
                        + mapHeight;

        graphics.drawString(
                "Center "
                        + map.centerWorldX()
                        + ","
                        + map.centerWorldZ(),
                LEFT_MARGIN,
                bottom + 18
        );

        graphics.drawString(
                "Radius "
                        + map.radius()
                        + " blocks",
                LEFT_MARGIN,
                bottom + 36
        );

        graphics.drawString(
                "Filter Y "
                        + map.yFilter()
                        .description(),
                LEFT_MARGIN,
                bottom + 54
        );

        graphics.drawString(
                "Matching blocks "
                        + map.matchingBlocks()
                        + " | hit columns "
                        + map.hitColumns(),
                LEFT_MARGIN,
                bottom + 68
        );

        if (!map.cells().isEmpty()) {
            graphics.drawString(
                    "Y range "
                            + map.minMatchedY()
                            + ".."
                            + map.maxMatchedY(),
                    LEFT_MARGIN,
                    bottom + 86
            );
        }
    }

    private void drawLegend(
            Graphics2D graphics,
            ActualBlockMap map,
            int mapHeight
    ) {
        int y =
                TOP_MARGIN
                        + mapHeight
                        + 108;

        graphics.setColor(
                TEXT
        );

        if (map.yFilter()
                .enabled()) {
            graphics.drawString(
                    "Filtered map: brighter/more saturated = more matching blocks in X/Z column",
                    LEFT_MARGIN,
                    y
            );

        } else {
            graphics.drawString(
                    "Legend: brighter = shallower, darker = deeper, more saturated = more blocks in X/Z column",
                    LEFT_MARGIN,
                    y
            );
        }
    }

    private Color colorFor(
            ActualBlockMapCell cell,
            ActualBlockMap map
    ) {
        if (map.yFilter()
                .enabled()) {

            double density =
                    Math.min(
                            1.0,
                            Math.log(
                                    cell.matchCount()
                                            + 1.0
                            )
                                    / Math.log(
                                    10.0
                            )
                    );

            return Color.getHSBColor(
                    0.055f,
                    (float) (0.55
                            + 0.40
                            * density),
                    (float) (0.35
                            + 0.60
                            * density)
            );
        }

        double yRange =
                Math.max(
                        1,
                        map.maxMatchedY()
                                - map.minMatchedY()
                );

        double depthNormalized =
                (cell.maxY()
                        - map.minMatchedY())
                        / yRange;

        double densityNormalized =
                Math.min(
                        1.0,
                        Math.log(
                                cell.matchCount()
                                        + 1.0
                        )
                                / Math.log(
                                10.0
                        )
                );

        float hue =
                (float) (0.02
                        + 0.06
                        * depthNormalized);

        float saturation =
                (float) (0.60
                        + 0.35
                        * densityNormalized);

        float brightness =
                (float) (0.35
                        + 0.55
                        * depthNormalized);

        return Color.getHSBColor(
                hue,
                saturation,
                brightness
        );
    }

    private void validateSize(
            int width,
            int height
    ) {
        if (width <= 0
                || height <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive"
            );
        }

        if (width > MAX_IMAGE_DIMENSION
                || height > MAX_IMAGE_DIMENSION) {

            throw new IllegalArgumentException(
                    "image would be "
                            + width
                            + "x"
                            + height
                            + "; maximum dimension is "
                            + MAX_IMAGE_DIMENSION
            );
        }
    }
}
