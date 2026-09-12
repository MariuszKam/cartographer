package cartographer.render;

import cartographer.environment.EnvironmentLabel;
import cartographer.environment.EnvironmentProfile;
import cartographer.model.MapChunk;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.WorldPosition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

public class EnvironmentOverlayRenderer {

    private static final int REGION_SIZE_BLOCKS =
            MapRegionCoordinate.SIZE_MAP_CHUNKS
                    * MapChunk.SIZE;

    private static final int ALPHA =
            58;

    public OverlayRenderReport draw(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            List<EnvironmentProfile> profiles
    ) {
        if (image == null
                || center == null
                || profiles == null
                || profiles.isEmpty()) {

            return OverlayRenderReport.none();
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

        int maxWorldXExclusive =
                minWorldX
                        + radiusBlocks
                        * 2;

        int maxWorldZExclusive =
                minWorldZ
                        + radiusBlocks
                        * 2;

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

        int candidates =
                0;

        int drawn =
                0;

        int unavailable =
                0;

        Graphics2D graphics =
                image.createGraphics();

        try {
            for (EnvironmentProfile profile : profiles) {
                if (profile == null
                        || profile.coordinate() == null) {

                    continue;
                }

                int regionMinX =
                        profile.coordinate().x()
                                * REGION_SIZE_BLOCKS;

                int regionMinZ =
                        profile.coordinate().z()
                                * REGION_SIZE_BLOCKS;

                int regionMaxXExclusive =
                        regionMinX
                                + REGION_SIZE_BLOCKS;

                int regionMaxZExclusive =
                        regionMinZ
                                + REGION_SIZE_BLOCKS;

                if (!intersects(
                        regionMinX,
                        regionMinZ,
                        regionMaxXExclusive,
                        regionMaxZExclusive,
                        minWorldX,
                        minWorldZ,
                        maxWorldXExclusive,
                        maxWorldZExclusive
                )) {

                    continue;
                }

                candidates++;

                if (!hasEnvironmentData(
                        profile
                )) {
                    unavailable++;
                    continue;
                }

                Color color =
                        colorFor(
                                profile
                        );

                int imageMinX =
                        (int) Math.floor(
                                (regionMinX - minWorldX)
                                        * scaleX
                        );

                int imageMinY =
                        (int) Math.floor(
                                (regionMinZ - minWorldZ)
                                        * scaleZ
                        );

                int imageMaxX =
                        (int) Math.ceil(
                                (regionMaxXExclusive - minWorldX)
                                        * scaleX
                        );

                int imageMaxY =
                        (int) Math.ceil(
                                (regionMaxZExclusive - minWorldZ)
                                        * scaleZ
                        );

                int clippedMinX =
                        Math.max(
                                0,
                                imageMinX
                        );

                int clippedMinY =
                        Math.max(
                                0,
                                imageMinY
                        );

                int clippedMaxX =
                        Math.min(
                                image.getWidth(),
                                imageMaxX
                        );

                int clippedMaxY =
                        Math.min(
                                image.getHeight(),
                                imageMaxY
                        );

                if (clippedMinX >= clippedMaxX
                        || clippedMinY >= clippedMaxY) {

                    continue;
                }

                graphics.setColor(
                        color
                );

                graphics.fillRect(
                        clippedMinX,
                        clippedMinY,
                        clippedMaxX
                                - clippedMinX,
                        clippedMaxY
                                - clippedMinY
                );

                graphics.setColor(
                        new Color(
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue(),
                                Math.min(
                                        150,
                                        ALPHA
                                                + 60
                                )
                        )
                );

                graphics.drawRect(
                        clippedMinX,
                        clippedMinY,
                        Math.max(
                                0,
                                clippedMaxX
                                        - clippedMinX
                                        - 1
                        ),
                        Math.max(
                                0,
                                clippedMaxY
                                        - clippedMinY
                                        - 1
                        )
                );

                drawn++;
            }

        } finally {
            graphics.dispose();
        }

        return new OverlayRenderReport(
                candidates,
                drawn,
                unavailable
        );
    }

    private boolean hasEnvironmentData(
            EnvironmentProfile profile
    ) {
        return profile.climate()
                .isPresent()
                || profile.forest()
                .isPresent()
                || profile.ocean()
                .isPresent()
                || profile.landform()
                .isPresent();
    }

    private Color colorFor(
            EnvironmentProfile profile
    ) {
        Set<EnvironmentLabel> labels =
                profile.labels();

        int red =
                110;

        int green =
                125;

        int blue =
                110;

        int contributors =
                1;

        if (labels.contains(
                EnvironmentLabel.COLD
        )) {
            red += 45;
            green += 105;
            blue += 210;
            contributors++;
        }

        if (labels.contains(
                EnvironmentLabel.HOT
        )) {
            red += 230;
            green += 105;
            blue += 55;
            contributors++;
        }

        if (labels.contains(
                EnvironmentLabel.ARID
        )) {
            red += 205;
            green += 175;
            blue += 95;
            contributors++;
        }

        if (labels.contains(
                EnvironmentLabel.HUMID
        )) {
            red += 55;
            green += 180;
            blue += 185;
            contributors++;
        }

        if (labels.contains(
                EnvironmentLabel.OPEN
        )) {
            red += 155;
            green += 155;
            blue += 140;
            contributors++;
        }

        if (labels.contains(
                EnvironmentLabel.FORESTED
        )) {
            red += 55;
            green += 175;
            blue += 75;
            contributors++;
        }

        if (labels.isEmpty()) {
            if (profile.forest()
                    .isPresent()) {

                double density =
                        profile.forest()
                                .get()
                                .averageNormalizedDensity();

                red =
                        (int) Math.round(
                                150
                                        - density
                                        * 65
                        );

                green =
                        (int) Math.round(
                                125
                                        + density
                                        * 90
                        );

                blue =
                        105;
            }

            if (profile.climate()
                    .isPresent()) {

                double temperature =
                        profile.climate()
                                .get()
                                .averageTemperatureIndex()
                                / 255.0;

                double rainfall =
                        profile.climate()
                                .get()
                                .averageRainfallIndex()
                                / 255.0;

                red =
                        clamp(
                                (int) Math.round(
                                        red
                                                + temperature
                                                * 55
                                )
                        );

                green =
                        clamp(
                                (int) Math.round(
                                        green
                                                + rainfall
                                                * 45
                                )
                        );

                blue =
                        clamp(
                                (int) Math.round(
                                        blue
                                                + (1.0 - temperature)
                                                * 40
                                )
                        );
            }

            return new Color(
                    clamp(red),
                    clamp(green),
                    clamp(blue),
                    ALPHA
            );
        }

        return new Color(
                clamp(
                        red / contributors
                ),
                clamp(
                        green / contributors
                ),
                clamp(
                        blue / contributors
                ),
                ALPHA
        );
    }

    private boolean intersects(
            int firstMinX,
            int firstMinZ,
            int firstMaxX,
            int firstMaxZ,
            int secondMinX,
            int secondMinZ,
            int secondMaxX,
            int secondMaxZ
    ) {
        return firstMinX < secondMaxX
                && firstMaxX > secondMinX
                && firstMinZ < secondMaxZ
                && firstMaxZ > secondMinZ;
    }

    private int clamp(
            int value
    ) {
        return Math.clamp(
                value
                ,
                0,
                255);
    }
}