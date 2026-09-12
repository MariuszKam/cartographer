package cartographer.render;

import cartographer.cli.ProgressReporter;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldPosition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MapRenderer {

    private static final int MAX_IMAGE_SIZE =
            4096;

    private static final int MIN_LEGEND_WIDTH =
            180;

    private static final int MIN_LEGEND_HEIGHT =
            120;

    private final TerrainPalette palette =
            new TerrainPalette();

    private final SemanticTerrainPalette semanticPalette =
            new SemanticTerrainPalette();

    private final MarkerRenderer markers =
            new MarkerRenderer();

    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            List<MapChunk> chunks,
            List<SurfaceBlock> surfaceBlocks,
            RenderOptions options
    ) {
        return render(
                center,
                player,
                home,
                chunks,
                surfaceBlocks,
                options,
                ProgressReporter.NONE
        );
    }

    public RenderedMap render(
            WorldPosition center,
            HomeState home,
            List<MapChunk> chunks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        return render(
                center,
                center,
                home,
                chunks,
                List.of(),
                options,
                progress
        );
    }

    public RenderedMap render(
            WorldPosition center,
            HomeState home,
            List<MapChunk> chunks,
            List<SurfaceBlock> surfaceBlocks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        return render(
                center,
                center,
                home,
                chunks,
                surfaceBlocks,
                options,
                progress
        );
    }

    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            List<MapChunk> chunks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        return render(
                center,
                player,
                home,
                chunks,
                List.of(),
                options,
                progress
        );
    }

    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            List<MapChunk> chunks,
            List<SurfaceBlock> surfaceBlocks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                home,
                "Home state is required"
        );

        int diameter =
                Math.clamp(
                        (long) options.radiusBlocks()
                                * 2
                                * options.pixelsPerBlock()
                                + 1,
                        64,
                        MAX_IMAGE_SIZE
                );

        double scale =
                diameter
                        / (double) (
                        options.radiusBlocks()
                                * 2
                );

        BufferedImage image =
                new BufferedImage(
                        diameter,
                        diameter,
                        BufferedImage.TYPE_INT_ARGB
                );

        prepareBackground(
                image,
                options,
                progress
        );

        int minX =
                (int) Math.floor(
                        center.x()
                )
                        - options.radiusBlocks();

        int minZ =
                (int) Math.floor(
                        center.z()
                )
                        - options.radiusBlocks();

        boolean terrainEnabled =
                options.layers()
                        .contains(
                                RenderLayer.TERRAIN
                        );

        boolean surfaceEnabled =
                options.layers()
                        .contains(
                                RenderLayer.SURFACE
                        );

        HeightSamples samples =
                HeightSamples.empty();

        if (terrainEnabled
                || surfaceEnabled) {

            samples =
                    collectHeightSamples(
                            chunks,
                            minX,
                            minZ,
                            options.radiusBlocks()
                                    * 2,
                            progress
                    );
        }

        int tilesDrawn =
                0;

        if (terrainEnabled) {
            tilesDrawn =
                    drawTerrain(
                            image,
                            samples,
                            minX,
                            minZ,
                            scale,
                            diameter,
                            options,
                            progress
                    );
        }

        if (surfaceEnabled) {
            drawSurfaceBlocks(
                    image,
                    surfaceBlocks,
                    samples,
                    minX,
                    minZ,
                    scale,
                    diameter,
                    progress
            );
        }

        if (surfaceEnabled) {
            drawLegend(
                    image,
                    surfaceBlocks
            );
        }

        int markerCount =
                drawMarkers(
                        image,
                        player,
                        home,
                        minX,
                        minZ,
                        scale,
                        options,
                        progress
                );

        String layers =
                options.layers()
                        .stream()
                        .map(
                                Enum::name
                        )
                        .sorted()
                        .collect(
                                Collectors.joining(
                                        ","
                                )
                        );

        return new RenderedMap(
                image,
                new MapRenderReport(
                        diameter,
                        diameter,
                        chunks.size(),
                        tilesDrawn,
                        markerCount,
                        options.style(),
                        layers
                )
        );
    }

    private void prepareBackground(
            BufferedImage image,
            RenderOptions options,
            ProgressReporter progress
    ) {
        progress.start(
                "Preparing image background"
        );

        for (int y = 0;
             y < image.getHeight();
             y++) {

            for (int x = 0;
                 x < image.getWidth();
                 x++) {

                image.setRGB(
                        x,
                        y,
                        palette.background(
                                options.style()
                        )
                );
            }

            progress.progress(
                    "Preparing image background",
                    y + 1,
                    image.getHeight()
            );
        }
    }

    private int drawTerrain(
            BufferedImage image,
            HeightSamples samples,
            int minX,
            int minZ,
            double scale,
            int diameter,
            RenderOptions options,
            ProgressReporter progress
    ) {
        int tilesDrawn =
                0;

        progress.start(
                "Drawing terrain"
        );

        for (int imageY = 0;
             imageY < diameter;
             imageY++) {

            int worldZ =
                    minZ
                            + Math.min(
                            options.radiusBlocks()
                                    * 2
                                    - 1,
                            (int) Math.floor(
                                    imageY
                                            / scale
                            )
                    );

            for (int imageX = 0;
                 imageX < diameter;
                 imageX++) {

                int worldX =
                        minX
                                + Math.min(
                                options.radiusBlocks()
                                        * 2
                                        - 1,
                                (int) Math.floor(
                                        imageX
                                                / scale
                                )
                        );

                Integer height =
                        samples.heightAt(
                                worldX,
                                worldZ
                        );

                if (height == null) {
                    continue;
                }

                image.setRGB(
                        imageX,
                        imageY,
                        palette.terrainColor(
                                height,
                                samples.minHeight(),
                                samples.maxHeight(),
                                hillshade(
                                        samples,
                                        worldX,
                                        worldZ
                                ),
                                options.style()
                        )
                );

                tilesDrawn++;
            }

            progress.progress(
                    "Drawing terrain",
                    imageY + 1,
                    diameter
            );
        }

        return tilesDrawn;
    }

    private void drawSurfaceBlocks(
            BufferedImage image,
            List<SurfaceBlock> surfaceBlocks,
            HeightSamples samples,
            int minX,
            int minZ,
            double scale,
            int diameter,
            ProgressReporter progress
    ) {
        progress.start(
                "Drawing semantic surface"
        );

        for (int index = 0;
             index < surfaceBlocks.size();
             index++) {

            SurfaceBlock block =
                    surfaceBlocks.get(
                            index
                    );

            int startX =
                    (int) Math.floor(
                            (block.worldX()
                                    - minX)
                                    * scale
                    );

            int endX =
                    (int) Math.ceil(
                            (block.worldX()
                                    + 1
                                    - minX)
                                    * scale
                    );

            int startY =
                    (int) Math.floor(
                            (block.worldZ()
                                    - minZ)
                                    * scale
                    );

            int endY =
                    (int) Math.ceil(
                            (block.worldZ()
                                    + 1
                                    - minZ)
                                    * scale
                    );

            if (endX <= 0
                    || endY <= 0
                    || startX >= diameter
                    || startY >= diameter) {

                progress.progress(
                        "Drawing semantic surface",
                        index + 1,
                        surfaceBlocks.size()
                );

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
                            diameter,
                            endX
                    );

            endY =
                    Math.min(
                            diameter,
                            endY
                    );

            double shade =
                    samples.heightAt(
                            block.worldX(),
                            block.worldZ()
                    ) == null
                            ? 0.0
                            : hillshade(
                            samples,
                            block.worldX(),
                            block.worldZ()
                    );

            int color =
                    semanticPalette.color(
                            block.surfaceClass(),
                            shade
                    );

            for (int imageY = startY;
                 imageY < endY;
                 imageY++) {

                for (int imageX = startX;
                     imageX < endX;
                     imageX++) {

                    image.setRGB(
                            imageX,
                            imageY,
                            color
                    );
                }
            }

            progress.progress(
                    "Drawing semantic surface",
                    index + 1,
                    surfaceBlocks.size()
            );
        }
    }

    private int drawMarkers(
            BufferedImage image,
            WorldPosition player,
            HomeState home,
            int minX,
            int minZ,
            double scale,
            RenderOptions options,
            ProgressReporter progress
    ) {
        int markerCount =
                0;

        progress.start(
                "Drawing markers"
        );

        if (options.layers()
                .contains(
                        RenderLayer.MARKERS
                )) {

            Graphics2D graphics =
                    image.createGraphics();

            try {
                int playerX =
                        (int) Math.round(
                                (player.x()
                                        - minX)
                                        * scale
                        );

                int playerY =
                        (int) Math.round(
                                (player.z()
                                        - minZ)
                                        * scale
                        );

                markers.drawCross(
                        graphics,
                        playerX,
                        playerY,
                        Color.RED
                );

                markerCount++;

                if (home instanceof HomeState.Present(HomeLocation location)) {

                    int homeX =
                            (int) Math.round(
                                    (location.x()
                                            - minX)
                                            * scale
                            );

                    int homeY =
                            (int) Math.round(
                                    (location.z()
                                            - minZ)
                                            * scale
                            );

                    markers.drawCross(
                            graphics,
                            homeX,
                            homeY,
                            Color.CYAN
                    );

                    markerCount++;
                }

            } finally {
                graphics.dispose();
            }
        }

        progress.done(
                "Markers drawn"
        );

        return markerCount;
    }

    private void drawLegend(
            BufferedImage image,
            List<SurfaceBlock> surfaceBlocks
    ) {
        /*
         * A legend on tiny images is not useful and can cover the
         * actual data completely.
         *
         * Real maps such as our 257x257 render still get the legend.
         */
        if (image.getWidth() < MIN_LEGEND_WIDTH
                || image.getHeight() < MIN_LEGEND_HEIGHT) {

            return;
        }

        Set<SurfaceClass> classes =
                EnumSet.noneOf(
                        SurfaceClass.class
                );

        for (SurfaceBlock block :
                surfaceBlocks) {

            classes.add(
                    block.surfaceClass()
            );
        }

        if (classes.isEmpty()) {
            return;
        }

        Graphics2D graphics =
                image.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            int lineHeight =
                    14;

            int width =
                    160;

            int titleHeight =
                    18;

            int height =
                    8
                            + titleHeight
                            + classes.size()
                            * lineHeight;

            int x =
                    8;

            int y =
                    Math.max(
                            8,
                            image.getHeight()
                                    - height
                                    - 8
                    );

            graphics.setColor(
                    new Color(
                            0,
                            0,
                            0,
                            180
                    )
            );

            graphics.fillRect(
                    x,
                    y,
                    width,
                    height
            );

            graphics.setColor(
                    Color.WHITE
            );

            graphics.drawString(
                    "Surface",
                    x + 6,
                    y + 13
            );

            int line =
                    0;

            for (SurfaceClass surfaceClass :
                    SurfaceClass.values()) {

                if (!classes.contains(
                        surfaceClass
                )) {
                    continue;
                }

                int rowY =
                        y
                                + titleHeight
                                + 4
                                + line
                                * lineHeight;

                graphics.setColor(
                        new Color(
                                semanticPalette.color(
                                        surfaceClass,
                                        0.0
                                ),
                                true
                        )
                );

                graphics.fillRect(
                        x + 6,
                        rowY,
                        10,
                        10
                );

                graphics.setColor(
                        Color.WHITE
                );

                graphics.drawString(
                        surfaceClass.label(),
                        x + 22,
                        rowY + 10
                );

                line++;
            }

        } finally {
            graphics.dispose();
        }
    }

    private HeightSamples collectHeightSamples(
            List<MapChunk> chunks,
            int minX,
            int minZ,
            int sizeBlocks,
            ProgressReporter progress
    ) {
        Map<Long, Integer> heights =
                new HashMap<>(
                        chunks.size()
                                * MapChunk.HEIGHT_VALUE_COUNT
                );

        int minHeight =
                Integer.MAX_VALUE;

        int maxHeight =
                Integer.MIN_VALUE;

        for (int index = 0;
             index < chunks.size();
             index++) {

            MapChunk chunk =
                    chunks.get(
                            index
                    );

            progress.progress(
                    "Indexing mapchunk heights",
                    index + 1,
                    chunks.size()
            );

            int originX =
                    chunk.coordinate()
                            .x()
                            * MapChunk.SIZE;

            int originZ =
                    chunk.coordinate()
                            .z()
                            * MapChunk.SIZE;

            for (int localZ = 0;
                 localZ < MapChunk.SIZE;
                 localZ++) {

                for (int localX = 0;
                     localX < MapChunk.SIZE;
                     localX++) {

                    int worldX =
                            originX
                                    + localX;

                    int worldZ =
                            originZ
                                    + localZ;

                    if (worldX < minX
                            || worldX >= minX
                            + sizeBlocks
                            || worldZ < minZ
                            || worldZ >= minZ
                            + sizeBlocks) {

                        continue;
                    }

                    int height =
                            chunk.terrainHeightAt(
                                    localX,
                                    localZ
                            );

                    heights.put(
                            key(
                                    worldX,
                                    worldZ
                            ),
                            height
                    );

                    minHeight =
                            Math.min(
                                    minHeight,
                                    height
                            );

                    maxHeight =
                            Math.max(
                                    maxHeight,
                                    height
                            );
                }
            }
        }

        if (heights.isEmpty()) {
            minHeight =
                    0;

            maxHeight =
                    0;
        }

        return new HeightSamples(
                heights,
                minHeight,
                maxHeight
        );
    }

    private double hillshade(
            HeightSamples samples,
            int worldX,
            int worldZ
    ) {
        Integer west =
                samples.heightAt(
                        worldX - 1,
                        worldZ
                );

        Integer east =
                samples.heightAt(
                        worldX + 1,
                        worldZ
                );

        Integer north =
                samples.heightAt(
                        worldX,
                        worldZ - 1
                );

        Integer south =
                samples.heightAt(
                        worldX,
                        worldZ + 1
                );

        if (west == null
                || east == null
                || north == null
                || south == null) {

            return 0.0;
        }

        double dx =
                east - west;

        double dz =
                south - north;

        double light =
                (-dx * 0.55
                        - dz * 0.75)
                        / 32.0;

        return Math.clamp(
                light,
                -0.35,
                0.35
        );
    }

    private long key(
            int worldX,
            int worldZ
    ) {
        return ((long) worldX << 32)
                ^ Integer.toUnsignedLong(
                worldZ
        );
    }

    private record HeightSamples(
            Map<Long, Integer> heights,
            int minHeight,
            int maxHeight
    ) {

        static HeightSamples empty() {
            return new HeightSamples(
                    Map.of(),
                    0,
                    0
            );
        }

        Integer heightAt(
                int worldX,
                int worldZ
        ) {
            return heights.get(
                    ((long) worldX << 32)
                            ^ Integer.toUnsignedLong(
                            worldZ
                    )
            );
        }
    }
}
