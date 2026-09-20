package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.MapChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldPosition;
import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceMap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

public class MapRenderer {

    private static final int MIN_LEGEND_WIDTH =
            180;

    private static final int MIN_LEGEND_HEIGHT =
            120;

    private final TerrainPalette palette =
            new TerrainPalette();

    private final SemanticTerrainPalette semanticPalette =
            new SemanticTerrainPalette();

    private final SoilFertilityOverlayRenderer soilFertilityRenderer =
            new SoilFertilityOverlayRenderer();

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

    /**
     * PF-3 raster-bounded Surface production path.
     *
     * <p>exactSurface is required only when SOIL_FERTILITY is enabled; the
     * alpha-composited soil overlay still follows the exact compatibility path
     * until it has its own parity-safe render-sized representation.</p>
     */
    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            MapTerrainPreparation terrain,
            SurfaceRenderData surfaceData,
            SurfaceMap exactSurface,
            Map<Integer, BlockInfo> registry,
            RenderOptions options,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(player, "player is required");
        Objects.requireNonNull(home, "Home state is required");
        Objects.requireNonNull(terrain, "terrain preparation is required");
        Objects.requireNonNull(surfaceData, "Surface render data is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(options, "render options are required");
        Objects.requireNonNull(progress, "progress is required");

        RenderSamplingPlan sampling = RenderSamplingPlan.from(center, options);
        int diameter = sampling.rasterSize();
        double scale = sampling.effectivePixelsPerBlock();
        BufferedImage image = new BufferedImage(
                diameter,
                diameter,
                BufferedImage.TYPE_INT_ARGB
        );
        ArgbRaster raster = ArgbRaster.wrap(image);
        prepareBackground(raster, options, progress);

        int tilesDrawn = options.layers().contains(RenderLayer.TERRAIN)
                ? drawTerrain(
                        raster,
                        terrain.heights(),
                        sampling,
                        options,
                        progress
                )
                : 0;

        if (options.layers().contains(RenderLayer.SURFACE)) {
            drawSurfaceRenderData(
                    raster,
                    surfaceData,
                    terrain.surfaceHeights(),
                    progress
            );
        }

        if (options.layers().contains(RenderLayer.SOIL_FERTILITY)) {
            if (exactSurface == null) {
                throw new IllegalStateException(
                        "exact Surface data is required for soil fertility"
                );
            }
            soilFertilityRenderer.draw(
                    image,
                    exactSurface,
                    registry,
                    sampling.worldMinX(),
                    sampling.worldMinZ(),
                    scale,
                    progress
            );
        }

        if (options.layers().contains(RenderLayer.SURFACE)) {
            drawLegend(image, surfaceData);
        }

        int markerCount = drawMarkers(
                image,
                player,
                home,
                sampling.geometry(),
                options,
                progress
        );
        String layers = options.layers().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));

        return new RenderedMap(
                image,
                new MapRenderReport(
                        diameter,
                        diameter,
                        terrain.mapChunkCount(),
                        tilesDrawn,
                        markerCount,
                        options.style(),
                        layers
                ),
                sampling.geometry()
        );
    }

    public RenderedMap render(
            WorldPosition center,
            HomeState home,
            List<MapChunk> chunks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        return render(center, center, home, chunks, options, progress);
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
        MapTerrainPreparation.Builder builder = MapTerrainPreparation.builder(
                center, options, chunks.size(), progress);
        chunks.forEach(builder::accept);
        return render(
                center,
                player,
                home,
                builder.finish(),
                SurfaceRenderData.empty(
                        RenderSamplingPlan.from(center, options)
                ),
                null,
                Map.of(),
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
        MapTerrainPreparation.Builder terrainBuilder =
                MapTerrainPreparation.builder(
                        center,
                        options,
                        chunks.size(),
                        progress
                );
        for (MapChunk chunk : chunks) {
            terrainBuilder.accept(chunk);
        }
        return render(
                center,
                player,
                home,
                terrainBuilder.finish(),
                surfaceBlocks,
                options,
                progress
        );
    }

    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            MapTerrainPreparation terrain,
            List<SurfaceBlock> surfaceBlocks,
            RenderOptions options,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(
                home,
                "Home state is required"
        );
        Objects.requireNonNull(terrain, "terrain preparation is required");

        RenderSamplingPlan sampling =
                RenderSamplingPlan.from(center, options);

        int diameter =
                sampling.rasterSize();

        double scale =
                sampling.effectivePixelsPerBlock();

        BufferedImage image =
                new BufferedImage(
                        diameter,
                        diameter,
                        BufferedImage.TYPE_INT_ARGB
                );

        ArgbRaster raster = ArgbRaster.wrap(image);

        prepareBackground(
                raster,
                options,
                progress
        );

        int minX =
                sampling.worldMinX();

        int minZ =
                sampling.worldMinZ();

        MapViewportGeometry geometry =
                sampling.geometry();

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

        TerrainHeightField terrainSamples = terrain.heights();
        DenseHeightGrid surfaceSamples = terrain.surfaceHeights();

        int tilesDrawn =
                0;

        if (terrainEnabled) {
            tilesDrawn =
                    drawTerrain(
                            raster,
                            terrainSamples,
                            sampling,
                            options,
                            progress
                    );
        }

        if (surfaceEnabled) {
            drawSurfaceBlocks(
                    raster,
                    surfaceBlocks,
                    surfaceSamples,
                    minX,
                    minZ,
                    scale,
                    diameter,
                    progress
            );
        }

        boolean soilFertilityEnabled =
                options.layers()
                        .contains(
                                RenderLayer.SOIL_FERTILITY
                        );

        if (soilFertilityEnabled) {
            soilFertilityRenderer.draw(
                    image,
                    surfaceBlocks,
                    minX,
                    minZ,
                    scale,
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
                        geometry,
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
                        terrain.mapChunkCount(),
                        tilesDrawn,
                        markerCount,
                        options.style(),
                        layers
                ),
                geometry
        );
    }

    public RenderedMap render(
            WorldPosition center,
            WorldPosition player,
            HomeState home,
            MapTerrainPreparation terrain,
            List<SurfaceBlock> surfaceBlocks,
            RenderOptions options
    ) {
        return render(
                center,
                player,
                home,
                terrain,
                surfaceBlocks,
                options,
                ProgressReporter.NONE
        );
    }

    private void prepareBackground(
            ArgbRaster raster,
            RenderOptions options,
            ProgressReporter progress
    ) {
        progress.start(
                "Preparing image background"
        );

        int background = palette.background(options.style());
        for (int y = 0;
             y < raster.height();
             y++) {
            raster.fillRow(y, background);

            progress.progress(
                    "Preparing image background",
                    y + 1,
                    raster.height()
            );
        }
    }

    private int drawTerrain(
            ArgbRaster raster,
            TerrainHeightField samples,
            RenderSamplingPlan sampling,
            RenderOptions options,
            ProgressReporter progress
    ) {
        int tilesDrawn =
                0;

        int diameter =
                sampling.rasterSize();

        progress.start(
                "Drawing terrain"
        );

        for (int imageY = 0;
             imageY < diameter;
             imageY++) {

            int worldZ =
                    sampling.worldZForImageRow(imageY);

            for (int imageX = 0;
                 imageX < diameter;
                 imageX++) {

                int worldX =
                        sampling.worldXForImageColumn(imageX);

                if (!samples.hasHeightAt(worldX, worldZ)) {
                    continue;
                }

                int height = samples.heightAt(worldX, worldZ);

                raster.setArgb(
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
            ArgbRaster raster,
            List<SurfaceBlock> surfaceBlocks,
            DenseHeightGrid samples,
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
                    !samples.hasHeightAt(
                            block.worldX(),
                            block.worldZ()
                    )
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

            raster.fillRect(startX, startY, endX, endY, color);

            progress.progress(
                    "Drawing semantic surface",
                    index + 1,
                    surfaceBlocks.size()
            );
        }
    }

    private void drawSurfaceRenderData(
            ArgbRaster raster,
            SurfaceRenderData surfaceData,
            TerrainHeightField samples,
            ProgressReporter progress
    ) {
        progress.start("Drawing render-sized semantic surface");
        if (surfaceData.isEmpty()) {
            progress.done("Semantic surface drawn");
            return;
        }

        int diameter = surfaceData.rasterSize();
        long[] sources = surfaceData.surfaceSourceByPixelView();
        byte[] classes = surfaceData.surfaceClassByPixelView();
        java.util.BitSet present = surfaceData.surfacePresentView();

        for (int index = present.nextSetBit(0);
             index >= 0;
             index = present.nextSetBit(index + 1)) {
            long packed = sources[index];
            int worldX = (int) (packed >> 32);
            int worldZ = (int) packed;
            double shade = !samples.hasHeightAt(worldX, worldZ)
                    ? 0.0
                    : hillshade(samples, worldX, worldZ);
            raster.setArgb(
                    index % diameter,
                    index / diameter,
                    semanticPalette.color(
                            cartographer.model.SurfaceClassCode.decode(
                                    classes[index]
                            ),
                            shade
                    )
            );
        }
        progress.done("Semantic surface drawn");
    }

    private int drawMarkers(
            BufferedImage image,
            WorldPosition player,
            HomeState home,
            MapViewportGeometry geometry,
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
                                geometry.absoluteWorldXToImageX(player.x())
                        );

                int playerY =
                        (int) Math.round(
                                geometry.absoluteWorldZToImageY(player.z())
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
                                    geometry.absoluteWorldXToImageX(location.x())
                            );

                    int homeY =
                            (int) Math.round(
                                    geometry.absoluteWorldZToImageY(location.z())
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
                            145
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

    private void drawLegend(
            BufferedImage image,
            SurfaceRenderData surfaceData
    ) {
        if (image.getWidth() < MIN_LEGEND_WIDTH
                || image.getHeight() < MIN_LEGEND_HEIGHT) {
            return;
        }
        Set<SurfaceClass> classes = surfaceData.surfaceClasses();
        if (classes.isEmpty()) {
            return;
        }

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );
            int lineHeight = 14;
            int width = 160;
            int titleHeight = 18;
            int height = 8 + titleHeight + classes.size() * lineHeight;
            int x = 8;
            int y = Math.max(8, image.getHeight() - height - 8);
            graphics.setColor(new Color(0, 0, 0, 145));
            graphics.fillRect(x, y, width, height);
            graphics.setColor(Color.WHITE);
            graphics.drawString("Surface", x + 6, y + 13);
            int line = 0;
            for (SurfaceClass surfaceClass : SurfaceClass.values()) {
                if (!classes.contains(surfaceClass)) {
                    continue;
                }
                int rowY = y + titleHeight + 4 + line++ * lineHeight;
                graphics.setColor(new Color(
                        semanticPalette.color(surfaceClass, 0.0),
                        true
                ));
                graphics.fillRect(x + 6, rowY, 10, 10);
                graphics.setColor(Color.WHITE);
                graphics.drawString(
                        surfaceClass.label(),
                        x + 22,
                        rowY + 10
                );
            }
        } finally {
            graphics.dispose();
        }
    }

    private double hillshade(
            TerrainHeightField samples,
            int worldX,
            int worldZ
    ) {
        if (!samples.hasHeightAt(worldX - 1, worldZ)
                || !samples.hasHeightAt(worldX + 1, worldZ)
                || !samples.hasHeightAt(worldX, worldZ - 1)
                || !samples.hasHeightAt(worldX, worldZ + 1)) {

            return 0.0;
        }

        int west = samples.heightAt(worldX - 1, worldZ);
        int east = samples.heightAt(worldX + 1, worldZ);
        int north = samples.heightAt(worldX, worldZ - 1);
        int south = samples.heightAt(worldX, worldZ + 1);

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

}
