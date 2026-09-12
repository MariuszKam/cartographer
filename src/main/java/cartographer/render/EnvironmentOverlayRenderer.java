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
                        imageCoordinate(
                                regionMinX,
                                minWorldX,
                                scaleX
                        );

                int imageMinY =
                        imageCoordinate(
                                regionMinZ,
                                minWorldZ,
                                scaleZ
                        );

                int imageMaxX =
                        imageCoordinate(
                                regionMaxXExclusive,
                                minWorldX,
                                scaleX
                        );

                int imageMaxY =
                        imageCoordinate(
                                regionMaxZExclusive,
                                minWorldZ,
                                scaleZ
                        );

                int clippedMinX =
                        Math.clamp(
                                imageMinX,
                                0,
                                image.getWidth()
                        );

                int clippedMinY =
                        Math.clamp(
                                imageMinY,
                                0,
                                image.getHeight()
                        );

                int clippedMaxX =
                        Math.clamp(
                                imageMaxX,
                                0,
                                image.getWidth()
                        );

                int clippedMaxY =
                        Math.clamp(
                                imageMaxY,
                                0,
                                image.getHeight()
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

    private int imageCoordinate(
            int worldCoordinate,
            int minWorldCoordinate,
            double scale
    ) {
        return (int) Math.floor(
                (worldCoordinate
                        - minWorldCoordinate)
                        * scale
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
                        Math.clamp(
                                (int) Math.round(
                                        red
                                                + temperature
                                                * 55
                                ),
                                0,
                                255
                        );

                green =
                        Math.clamp(
                                (int) Math.round(
                                        green
                                                + rainfall
                                                * 45
                                ),
                                0,
                                255
                        );

                blue =
                        Math.clamp(
                                (int) Math.round(
                                        blue
                                                + (1.0 - temperature)
                                                * 40
                                ),
                                0,
                                255
                        );
            }

            return new Color(
                    Math.clamp(
                            red,
                            0,
                            255
                    ),
                    Math.clamp(
                            green,
                            0,
                            255
                    ),
                    Math.clamp(
                            blue,
                            0,
                            255
                    ),
                    ALPHA
            );
        }

        return new Color(
                Math.clamp(
                        red / contributors,
                        0,
                        255
                ),
                Math.clamp(
                        green / contributors,
                        0,
                        255
                ),
                Math.clamp(
                        blue / contributors,
                        0,
                        255
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
}