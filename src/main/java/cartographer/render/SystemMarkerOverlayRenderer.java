package cartographer.render;

import cartographer.model.WorldPosition;
import cartographer.model.HomeState;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class SystemMarkerOverlayRenderer {

    private final MarkerRenderer markerRenderer =
            new MarkerRenderer();

    public void draw(
            BufferedImage image,
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            int radiusBlocks
    ) {
        if (image == null
                || center == null
                || player == null) {

            return;
        }

        Objects.requireNonNull(
                home,
                "Home state is required"
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

        try {
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

            if (home instanceof HomeState.Present(WorldPosition location)) {

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

        } finally {
            graphics.dispose();
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