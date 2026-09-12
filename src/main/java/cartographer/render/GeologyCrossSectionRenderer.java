package cartographer.render;

import cartographer.geology.crosssection.GeologyCrossSection;
import cartographer.geology.crosssection.GeologySectionColumn;
import cartographer.geology.crosssection.GeologySectionRun;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;

public class GeologyCrossSectionRenderer {

    private static final int LEFT_MARGIN =
            56;

    private static final int RIGHT_MARGIN =
            20;

    private static final int TOP_MARGIN =
            24;

    private static final int BOTTOM_MARGIN =
            118;

    private static final int MAX_IMAGE_DIMENSION =
            8192;

    private static final int EMPTY_WIDTH =
            420;

    private static final int EMPTY_HEIGHT =
            100;

    private static final int DISTANCE_TICK_STEP =
            128;

    private static final Color BACKGROUND =
            new Color(
                    17,
                    19,
                    22
            );

    private static final Color AIR =
            new Color(
                    8,
                    10,
                    12
            );

    private static final Color UNAVAILABLE =
            new Color(
                    67,
                    71,
                    76
            );

    private static final Color WATER =
            new Color(
                    49,
                    104,
                    174
            );

    private static final Color LAVA =
            new Color(
                    210,
                    74,
                    31
            );

    private static final Color SOIL =
            new Color(
                    100,
                    75,
                    48
            );

    private static final Color CLAY =
            new Color(
                    157,
                    91,
                    61
            );

    private static final Color COPPER_ORE =
            new Color(
                    208,
                    112,
                    67
            );

    private static final Color GENERIC_ORE =
            new Color(
                    222,
                    189,
                    83
            );

    private static final Color GRID =
            new Color(
                    255,
                    255,
                    255,
                    45
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

    private static final Color MARKER_LINE =
            new Color(
                    74,
                    192,
                    255,
                    180
            );

    private static final Color MARKER_DOT =
            new Color(
                    180,
                    231,
                    255
            );

    public BufferedImage render(
            GeologyCrossSection section,
            int horizontalScale,
            int verticalScale
    ) {
        return render(
                section,
                horizontalScale,
                verticalScale,
                null
        );
    }

    public BufferedImage render(
            GeologyCrossSection section,
            int horizontalScale,
            int verticalScale,
            GeologySectionMarker marker
    ) {
        Objects.requireNonNull(
                section,
                "section is required"
        );

        if (horizontalScale <= 0) {
            throw new IllegalArgumentException(
                    "horizontalScale must be positive"
            );
        }

        if (verticalScale <= 0) {
            throw new IllegalArgumentException(
                    "verticalScale must be positive"
            );
        }

        if (section.minYInclusive()
                == section.maxYExclusive()) {

            return renderEmpty(
                    section
            );
        }

        int verticalBlocks =
                Math.subtractExact(
                        section.maxYExclusive(),
                        section.minYInclusive()
                );

        int mapWidth =
                Math.multiplyExact(
                        section.columns().size(),
                        horizontalScale
                );

        int mapHeight =
                Math.multiplyExact(
                        verticalBlocks,
                        verticalScale
                );

        int width =
                Math.addExact(
                        Math.addExact(
                                LEFT_MARGIN,
                                mapWidth
                        ),
                        RIGHT_MARGIN
                );

        int height =
                Math.addExact(
                        Math.addExact(
                                TOP_MARGIN,
                                mapHeight
                        ),
                        BOTTOM_MARGIN
                );

        validateImageSize(
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

            drawSection(
                    graphics,
                    section,
                    horizontalScale,
                    verticalScale
            );

            drawVerticalGrid(
                    graphics,
                    section,
                    mapWidth,
                    verticalScale
            );

            drawFrame(
                    graphics,
                    mapWidth,
                    mapHeight
            );

            drawMarker(
                    graphics,
                    section,
                    marker,
                    horizontalScale,
                    verticalScale,
                    mapHeight
            );

            drawLabels(
                    graphics,
                    section,
                    mapWidth,
                    mapHeight
            );

            drawDistanceTicks(
                    graphics,
                    section,
                    mapWidth,
                    mapHeight,
                    horizontalScale
            );

            drawLegend(
                    graphics,
                    mapHeight
            );

        } finally {
            graphics.dispose();
        }

        return image;
    }

    private BufferedImage renderEmpty(
            GeologyCrossSection section
    ) {
        BufferedImage image =
                new BufferedImage(
                        EMPTY_WIDTH,
                        EMPTY_HEIGHT,
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
                    "No observed chunk data for geology section",
                    20,
                    38
            );

            graphics.drawString(
                    "From "
                            + section.startWorldX()
                            + ","
                            + section.startWorldZ()
                            + " to "
                            + section.endWorldX()
                            + ","
                            + section.endWorldZ(),
                    20,
                    62
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

    private void drawSection(
            Graphics2D graphics,
            GeologyCrossSection section,
            int horizontalScale,
            int verticalScale
    ) {
        for (GeologySectionColumn column :
                section.columns()) {

            int imageX =
                    LEFT_MARGIN
                            + column.index()
                            * horizontalScale;

            for (GeologySectionRun run :
                    column.runs()) {

                int imageY =
                        TOP_MARGIN
                                + (
                                section.maxYExclusive()
                                        - run.maxYExclusive()
                        )
                                * verticalScale;

                int height =
                        (
                                run.maxYExclusive()
                                        - run.minYInclusive()
                        )
                                * verticalScale;

                graphics.setColor(
                        colorFor(
                                run
                        )
                );

                graphics.fillRect(
                        imageX,
                        imageY,
                        horizontalScale,
                        height
                );

                if (!run.observed()) {
                    drawUnavailable(
                            graphics,
                            imageX,
                            imageY,
                            horizontalScale,
                            height
                    );
                }
            }
        }
    }

    private void drawUnavailable(
            Graphics2D graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        if (width < 3
                || height < 3) {

            return;
        }

        graphics.setColor(
                new Color(
                        210,
                        215,
                        220,
                        65
                )
        );

        for (int offset = -height;
             offset < width;
             offset += 6) {

            int startX =
                    x
                            + Math.max(
                            0,
                            offset
                    );

            int startY =
                    y
                            + Math.max(
                            0,
                            -offset
                    );

            int length =
                    Math.min(
                            width
                                    - Math.max(
                                    0,
                                    offset
                            ),
                            height
                                    - Math.max(
                                    0,
                                    -offset
                            )
                    );

            if (length > 0) {
                graphics.drawLine(
                        startX,
                        startY,
                        startX + length,
                        startY + length
                );
            }
        }
    }

    private void drawVerticalGrid(
            Graphics2D graphics,
            GeologyCrossSection section,
            int mapWidth,
            int verticalScale
    ) {
        graphics.setColor(
                GRID
        );

        for (int worldY = section.minYInclusive();
             worldY <= section.maxYExclusive();
             worldY++) {

            if (Math.floorMod(
                    worldY,
                    32
            ) != 0) {

                continue;
            }

            int imageY =
                    TOP_MARGIN
                            + (
                            section.maxYExclusive()
                                    - worldY
                    )
                            * verticalScale;

            graphics.drawLine(
                    LEFT_MARGIN,
                    imageY,
                    LEFT_MARGIN + mapWidth,
                    imageY
            );

            graphics.setColor(
                    TEXT
            );

            graphics.drawString(
                    Integer.toString(
                            worldY
                    ),
                    4,
                    imageY + 4
            );

            graphics.setColor(
                    GRID
            );
        }
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

    private void drawMarker(
            Graphics2D graphics,
            GeologyCrossSection section,
            GeologySectionMarker marker,
            int horizontalScale,
            int verticalScale,
            int mapHeight
    ) {
        if (marker == null) {
            return;
        }

        if (marker.columnIndex()
                >= section.columns().size()) {

            return;
        }

        int markerX =
                LEFT_MARGIN
                        + marker.columnIndex()
                        * horizontalScale
                        + Math.max(
                        0,
                        horizontalScale / 2
                );

        graphics.setColor(
                MARKER_LINE
        );

        graphics.drawLine(
                markerX,
                TOP_MARGIN,
                markerX,
                TOP_MARGIN + mapHeight
        );

        int clampedY =
                Math.max(
                        section.minYInclusive(),
                        Math.min(
                                marker.worldY(),
                                section.maxYExclusive() - 1
                        )
                );

        int markerY =
                TOP_MARGIN
                        + (
                        section.maxYExclusive()
                                - clampedY
                                - 1
                )
                        * verticalScale
                        + Math.max(
                        0,
                        verticalScale / 2
                );

        graphics.setColor(
                MARKER_DOT
        );

        graphics.fillOval(
                markerX - 4,
                markerY - 4,
                8,
                8
        );

        graphics.drawString(
                marker.label()
                        + " Y="
                        + marker.worldY(),
                Math.min(
                        markerX + 8,
                        LEFT_MARGIN
                                + (section.columns().size() - 1)
                                * horizontalScale
                ),
                Math.max(
                        14,
                        markerY - 8
                )
        );
    }

    private void drawLabels(
            Graphics2D graphics,
            GeologyCrossSection section,
            int mapWidth,
            int mapHeight
    ) {
        graphics.setColor(
                TEXT
        );

        graphics.drawString(
                "Observed geology cross-section",
                LEFT_MARGIN,
                16
        );

        int bottom =
                TOP_MARGIN
                        + mapHeight;

        graphics.drawString(
                "START "
                        + section.startWorldX()
                        + ","
                        + section.startWorldZ(),
                LEFT_MARGIN,
                bottom + 18
        );

        String end =
                "END "
                        + section.endWorldX()
                        + ","
                        + section.endWorldZ();

        int endWidth =
                graphics.getFontMetrics()
                        .stringWidth(
                                end
                        );

        graphics.drawString(
                end,
                Math.max(
                        LEFT_MARGIN,
                        LEFT_MARGIN
                                + mapWidth
                                - endWidth
                ),
                bottom + 18
        );

        graphics.drawString(
                "Y "
                        + section.minYInclusive()
                        + ".."
                        + (section.maxYExclusive() - 1),
                LEFT_MARGIN,
                bottom + 36
        );
    }

    private void drawDistanceTicks(
            Graphics2D graphics,
            GeologyCrossSection section,
            int mapWidth,
            int mapHeight,
            int horizontalScale
    ) {
        graphics.setColor(
                TEXT
        );

        int baselineY =
                TOP_MARGIN
                        + mapHeight
                        + 48;

        int tickTop =
                baselineY - 10;

        graphics.drawString(
                "Distance from START",
                LEFT_MARGIN,
                baselineY - 16
        );

        for (int index = 0;
             index < section.columns().size();
             index += DISTANCE_TICK_STEP) {

            int x =
                    LEFT_MARGIN
                            + index
                            * horizontalScale;

            graphics.drawLine(
                    x,
                    tickTop,
                    x,
                    baselineY
            );

            graphics.drawString(
                    index + "b",
                    x,
                    baselineY + 14
            );
        }

        int endX =
                LEFT_MARGIN
                        + mapWidth
                        - 1;

        graphics.drawLine(
                endX,
                tickTop,
                endX,
                baselineY
        );

        String endLabel =
                (section.columns().size() - 1)
                        + "b";

        int endLabelWidth =
                graphics.getFontMetrics()
                        .stringWidth(
                                endLabel
                        );

        graphics.drawString(
                endLabel,
                Math.max(
                        LEFT_MARGIN,
                        endX - endLabelWidth
                ),
                baselineY + 14
        );
    }

    private void drawLegend(
            Graphics2D graphics,
            int mapHeight
    ) {
        int baseY =
                TOP_MARGIN
                        + mapHeight
                        + 78;

        int x =
                LEFT_MARGIN;

        drawLegendItem(
                graphics,
                x,
                baseY,
                new Color(
                        153,
                        128,
                        126
                ),
                "granite"
        );

        x += 112;

        drawLegendItem(
                graphics,
                x,
                baseY,
                new Color(
                        65,
                        70,
                        75
                ),
                "basalt"
        );

        x += 108;

        drawLegendItem(
                graphics,
                x,
                baseY,
                new Color(
                        172,
                        143,
                        95
                ),
                "sandstone"
        );

        x += 128;

        drawLegendItem(
                graphics,
                x,
                baseY,
                COPPER_ORE,
                "copper ore"
        );

        x += 126;

        drawLegendItem(
                graphics,
                x,
                baseY,
                GENERIC_ORE,
                "other ore"
        );

        x += 116;

        drawLegendItem(
                graphics,
                x,
                baseY,
                AIR,
                "air / cave"
        );

        x += 112;

        drawLegendItem(
                graphics,
                x,
                baseY,
                UNAVAILABLE,
                "unavailable"
        );
    }

    private void drawLegendItem(
            Graphics2D graphics,
            int x,
            int y,
            Color color,
            String label
    ) {
        graphics.setColor(
                color
        );

        graphics.fillRect(
                x,
                y - 9,
                12,
                12
        );

        graphics.setColor(
                FRAME
        );

        graphics.drawRect(
                x,
                y - 9,
                12,
                12
        );

        graphics.setColor(
                TEXT
        );

        graphics.drawString(
                label,
                x + 18,
                y + 1
        );
    }

    private Color colorFor(
            GeologySectionRun run
    ) {
        if (!run.observed()) {
            return UNAVAILABLE;
        }

        String normalized =
                normalize(
                        run.blockCode()
                );

        if (isAir(
                normalized
        )) {
            return AIR;
        }

        if (normalized.contains(
                "water"
        )) {
            return WATER;
        }

        if (normalized.contains(
                "lava"
        )) {
            return LAVA;
        }

        if (isOre(
                normalized
        )) {
            if (normalized.contains(
                    "copper"
            )) {
                return COPPER_ORE;
            }

            return GENERIC_ORE;
        }

        if (normalized.contains(
                "forestfloor"
        )
                || normalized.contains(
                "soil"
        )
                || normalized.contains(
                "peat"
        )
                || normalized.contains(
                "mud"
        )) {

            return SOIL;
        }

        if (normalized.contains(
                "clay"
        )) {
            return CLAY;
        }

        if (normalized.contains(
                "granite"
        )) {
            return new Color(
                    153,
                    128,
                    126
            );
        }

        if (normalized.contains(
                "basalt"
        )) {
            return new Color(
                    65,
                    70,
                    75
            );
        }

        if (normalized.contains(
                "limestone"
        )) {
            return new Color(
                    168,
                    168,
                    153
            );
        }

        if (normalized.contains(
                "sandstone"
        )) {
            return new Color(
                    172,
                    143,
                    95
            );
        }

        if (normalized.contains(
                "shale"
        )) {
            return new Color(
                    89,
                    100,
                    106
            );
        }

        if (normalized.contains(
                "chalk"
        )) {
            return new Color(
                    207,
                    204,
                    183
            );
        }

        if (normalized.contains(
                "marble"
        )) {
            return new Color(
                    193,
                    193,
                    190
            );
        }

        if (normalized.contains(
                "slate"
        )) {
            return new Color(
                    76,
                    83,
                    91
            );
        }

        if (normalized.contains(
                "peridotite"
        )) {
            return new Color(
                    99,
                    114,
                    82
            );
        }

        if (normalized.startsWith(
                "unknown:"
        )) {
            return new Color(
                    151,
                    75,
                    154
            );
        }

        return stableColor(
                normalized
        );
    }

    private boolean isAir(
            String normalized
    ) {
        return normalized.equals(
                "air"
        )
                || normalized.equals(
                "game:air"
        )
                || normalized.endsWith(
                ":air"
        );
    }

    private boolean isOre(
            String normalized
    ) {
        return normalized.startsWith(
                "ore-"
        )
                || normalized.contains(
                ":ore-"
        )
                || normalized.contains(
                "-ore-"
        );
    }

    private String normalize(
            String code
    ) {
        return code == null
                ? ""
                : code.toLowerCase(
                Locale.ROOT
        );
    }

    private Color stableColor(
            String code
    ) {
        int hueDegrees =
                Math.floorMod(
                        code.hashCode(),
                        360
                );

        return Color.getHSBColor(
                hueDegrees / 360.0f,
                0.28f,
                0.62f
        );
    }

    private void validateImageSize(
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
                            + ". Reduce section length or scale."
            );
        }
    }
}
