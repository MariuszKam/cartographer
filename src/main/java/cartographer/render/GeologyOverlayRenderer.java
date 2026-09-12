package cartographer.render;

import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.MapChunk;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.WorldPosition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class GeologyOverlayRenderer {

    private static final int REGION_SIZE_BLOCKS =
            MapRegionCoordinate.SIZE_MAP_CHUNKS
                    * MapChunk.SIZE;

    private static final int ALPHA =
            66;

    public OverlayRenderReport draw(
            BufferedImage image,
            WorldPosition center,
            int radiusBlocks,
            List<GeologicProvinceSummary> summaries
    ) {
        if (image == null
                || center == null
                || summaries == null
                || summaries.isEmpty()) {

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
            for (GeologicProvinceSummary summary : summaries) {
                if (summary == null
                        || summary.coordinate() == null) {

                    continue;
                }

                int regionMinX =
                        summary.coordinate().x()
                                * REGION_SIZE_BLOCKS;

                int regionMinZ =
                        summary.coordinate().z()
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

                if (summary.dominantIds()
                        .isEmpty()) {

                    unavailable++;
                    continue;
                }

                int dominantRawId =
                        summary.dominantIds()
                                .getFirst();

                Color color =
                        colorForRawId(
                                dominantRawId
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

    private Color colorForRawId(
            int rawId
    ) {
        long unsigned =
                Integer.toUnsignedLong(
                        rawId
                );

        float hue =
                (float) (
                        (
                                unsigned
                                        * 0.6180339887498949
                        )
                                % 1.0
                );

        Color base =
                Color.getHSBColor(
                        hue,
                        0.55f,
                        0.95f
                );

        return new Color(
                base.getRed(),
                base.getGreen(),
                base.getBlue(),
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