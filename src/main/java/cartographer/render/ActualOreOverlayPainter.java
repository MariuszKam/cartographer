package cartographer.render;

import cartographer.model.WorldPosition;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMapCell;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class ActualOreOverlayPainter {

    private static final Color LABEL_BACKGROUND =
            new Color(
                    0,
                    0,
                    0,
                    170
            );

    private static final Color LABEL_TEXT =
            new Color(
                    255,
                    236,
                    204
            );

    public void paint(
            BufferedImage image,
            ActualBlockMap map,
            WorldPosition center,
            int radiusBlocks
    ) {
        Objects.requireNonNull(
                image,
                "image is required"
        );

        Objects.requireNonNull(
                map,
                "map is required"
        );

        Objects.requireNonNull(
                center,
                "center is required"
        );

        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException(
                    "radiusBlocks must be positive"
            );
        }

        Graphics2D graphics =
                image.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF
            );

            drawCells(
                    graphics,
                    image,
                    map,
                    center,
                    radiusBlocks
            );

            drawInfo(
                    graphics,
                    image,
                    map
            );

        } finally {
            graphics.dispose();
        }
    }

    private void drawCells(
            Graphics2D graphics,
            BufferedImage image,
            ActualBlockMap map,
            WorldPosition center,
            int radiusBlocks
    ) {
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

        for (ActualBlockMapCell cell :
                map.cells()) {

            int startX =
                    (int) Math.floor(
                            (cell.worldX()
                                    - minWorldX)
                                    * scaleX
                    );

            int endX =
                    (int) Math.ceil(
                            (cell.worldX()
                                    + 1
                                    - minWorldX)
                                    * scaleX
                    );

            int startY =
                    (int) Math.floor(
                            (cell.worldZ()
                                    - minWorldZ)
                                    * scaleZ
                    );

            int endY =
                    (int) Math.ceil(
                            (cell.worldZ()
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
                            Math.max(
                                    startX + 1,
                                    endX
                            )
                    );

            endY =
                    Math.min(
                            image.getHeight(),
                            Math.max(
                                    startY + 1,
                                    endY
                            )
                    );

            graphics.setColor(
                    colorFor(
                            cell
                    )
            );

            graphics.fillRect(
                    startX,
                    startY,
                    endX - startX,
                    endY - startY
            );
        }
    }

    private Color colorFor(
            ActualBlockMapCell cell
    ) {
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

        int alpha =
                (int) Math.round(
                        90
                                + 105
                                * density
                );

        int red =
                (int) Math.round(
                        225
                                + 25
                                * density
                );

        int green =
                (int) Math.round(
                        92
                                + 85
                                * density
                );

        int blue =
                (int) Math.round(
                        24
                                + 20
                                * density
                );

        return new Color(
                red,
                green,
                blue,
                alpha
        );
    }

    private void drawInfo(
            Graphics2D graphics,
            BufferedImage image,
            ActualBlockMap map
    ) {
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        graphics.setFont(
                graphics.getFont()
                        .deriveFont(
                                Font.PLAIN,
                                11.0f
                        )
        );

        int width =
                Math.min(
                        image.getWidth() - 8,
                        280
                );

        if (width <= 0
                || image.getHeight() < 36) {
            return;
        }

        graphics.setColor(
                LABEL_BACKGROUND
        );

        graphics.fillRect(
                4,
                4,
                width,
                36
        );

        graphics.setColor(
                LABEL_TEXT
        );

        graphics.drawString(
                "Actual ore overlay: "
                        + map.match(),
                10,
                18
        );

        graphics.drawString(
                "Y filter: "
                        + map.yFilter()
                        .description(),
                10,
                34
        );
    }
}
