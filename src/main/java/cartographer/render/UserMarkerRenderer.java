package cartographer.render;

import cartographer.marker.UserMarker;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

public class UserMarkerRenderer {

    private static final Color PIN_COLOR =
            new Color(
                    255,
                    193,
                    7
            );

    private static final Color PIN_OUTLINE =
            new Color(
                    40,
                    30,
                    0
            );

    private static final Color LABEL_BACKGROUND =
            new Color(
                    0,
                    0,
                    0,
                    190
            );

    private static final Color LABEL_TEXT =
            Color.WHITE;

    public int draw(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            List<UserMarker> markers,
            WorldMetadata metadata
    ) {
        if (image == null
                || center == null
                || metadata == null
                || markers == null
                || markers.isEmpty()) {

            return 0;
        }

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

            graphics.setStroke(
                    new BasicStroke(
                            2.0f
                    )
            );

            for (UserMarker marker : markers) {
                WorldPosition absolute =
                        metadata.toAbsolute(marker.position());

                int imageX =
                        (int) Math.round(
                                (absolute.x()
                                        - minWorldX)
                                        * scaleX
                        );

                int imageY =
                        (int) Math.round(
                                (absolute.z()
                                        - minWorldZ)
                                        * scaleZ
                        );

                if (!inside(
                        image,
                        imageX,
                        imageY
                )) {
                    continue;
                }

                drawPin(
                        graphics,
                        imageX,
                        imageY
                );

                drawLabel(
                        graphics,
                        image,
                        marker.name(),
                        imageX,
                        imageY
                );

                drawn++;
            }

        } finally {
            graphics.dispose();
        }

        return drawn;
    }

    private void drawPin(
            Graphics2D graphics,
            int x,
            int y
    ) {
        int radius =
                6;

        graphics.setColor(
                PIN_OUTLINE
        );

        graphics.fillOval(
                x - radius - 1,
                y - radius - 1,
                radius * 2 + 2,
                radius * 2 + 2
        );

        graphics.setColor(
                PIN_COLOR
        );

        graphics.fillOval(
                x - radius,
                y - radius,
                radius * 2,
                radius * 2
        );

        graphics.setColor(
                PIN_OUTLINE
        );

        graphics.drawLine(
                x,
                y + radius,
                x,
                y + radius + 6
        );
    }

    private void drawLabel(
            Graphics2D graphics,
            BufferedImage image,
            String text,
            int markerX,
            int markerY
    ) {
        FontMetrics metrics =
                graphics.getFontMetrics();

        int padding =
                4;

        int labelWidth =
                metrics.stringWidth(
                        text
                )
                        + padding * 2;

        int labelHeight =
                metrics.getHeight()
                        + padding * 2;

        int x =
                markerX
                        + 10;

        int y =
                markerY
                        - labelHeight
                        / 2;

        if (x + labelWidth
                >= image.getWidth()) {

            x =
                    markerX
                            - 10
                            - labelWidth;
        }

        if (y < 0) {
            y =
                    0;
        }

        if (y + labelHeight
                >= image.getHeight()) {

            y =
                    image.getHeight()
                            - labelHeight;
        }

        graphics.setColor(
                LABEL_BACKGROUND
        );

        graphics.fillRoundRect(
                x,
                y,
                labelWidth,
                labelHeight,
                8,
                8
        );

        graphics.setColor(
                PIN_COLOR
        );

        graphics.drawRoundRect(
                x,
                y,
                labelWidth,
                labelHeight,
                8,
                8
        );

        graphics.setColor(
                LABEL_TEXT
        );

        graphics.drawString(
                text,
                x + padding,
                y
                        + padding
                        + metrics.getAscent()
        );
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