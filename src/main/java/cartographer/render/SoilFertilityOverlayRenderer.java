package cartographer.render;

import cartographer.application.ProgressReporter;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceMap;
import cartographer.soil.SoilFertilityClassification;
import cartographer.soil.SoilFertilityClassifier;
import cartographer.soil.SoilFertilityTier;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

public final class SoilFertilityOverlayRenderer {
    private static final int LEGEND_MIN_WIDTH = 180;
    private static final int LEGEND_MIN_HEIGHT = 130;
    private static final int LEGEND_WIDTH = 145;
    private static final int LINE_HEIGHT = 14;

    private final SoilFertilityClassifier classifier =
            new SoilFertilityClassifier();

    private final SoilFertilityPalette palette =
            new SoilFertilityPalette();

    public int draw(
            BufferedImage image,
            List<SurfaceBlock> surfaceBlocks,
            int minX,
            int minZ,
            double scale,
            ProgressReporter progress
    ) {
        if (image == null) {
            throw new IllegalArgumentException("Image is required");
        }
        if (scale <= 0.0) {
            throw new IllegalArgumentException("Scale must be positive");
        }
        if (progress == null) {
            throw new IllegalArgumentException("Progress reporter is required");
        }

        List<SurfaceBlock> safeBlocks =
                surfaceBlocks == null ? List.of() : surfaceBlocks;

        progress.start("Drawing soil fertility");

        int drawn = 0;
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);

            for (int index = 0; index < safeBlocks.size(); index++) {
                SurfaceBlock block = safeBlocks.get(index);
                SoilFertilityClassification classification =
                        directlyVisibleFertility(block);

                if (classification != null) {
                    int startX = (int) Math.floor(
                            (block.worldX() - minX) * scale
                    );
                    int endX = Math.max(
                            startX + 1,
                            (int) Math.ceil(
                                    (block.worldX() + 1 - minX) * scale
                            )
                    );
                    int startY = (int) Math.floor(
                            (block.worldZ() - minZ) * scale
                    );
                    int endY = Math.max(
                            startY + 1,
                            (int) Math.ceil(
                                    (block.worldZ() + 1 - minZ) * scale
                            )
                    );

                    if (endX > 0
                            && endY > 0
                            && startX < image.getWidth()
                            && startY < image.getHeight()) {
                        startX = Math.max(0, startX);
                        startY = Math.max(0, startY);
                        endX = Math.min(image.getWidth(), endX);
                        endY = Math.min(image.getHeight(), endY);

                        graphics.setColor(palette.color(classification.tier()));
                        graphics.fillRect(
                                startX,
                                startY,
                                endX - startX,
                                endY - startY
                        );
                        drawn++;
                    }
                }

                progress.progress(
                        "Drawing soil fertility",
                        index + 1,
                        safeBlocks.size()
                );
            }

            if (drawn > 0) {
                drawLegend(graphics, image);
            }
        } finally {
            graphics.dispose();
        }

        progress.done("Soil fertility drawn");
        return drawn;
    }

    public int draw(
            BufferedImage image,
            SurfaceMap surface,
            Map<Integer, BlockInfo> registry,
            int minX,
            int minZ,
            double scale,
            ProgressReporter progress
    ) {
        if (image == null || surface == null || registry == null) {
            throw new IllegalArgumentException("Image, SurfaceMap, and registry are required");
        }
        if (scale <= 0.0 || progress == null) {
            throw new IllegalArgumentException("Scale and progress reporter are required");
        }
        progress.start("Drawing soil fertility");
        int[] drawn = {0};
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            surface.forEachResolvedCell((worldX, worldZ, y, blockId, liquidId, surfaceClass) -> {
                if (surfaceClass == SurfaceClass.WATER || surfaceClass == SurfaceClass.SNOW) {
                    return;
                }
                SoilFertilityClassification classification = classifier.classify(
                        registry.getOrDefault(blockId, BlockInfo.unknown(blockId))).orElse(null);
                if (classification == null) {
                    return;
                }
                int startX = (int) Math.floor((worldX - minX) * scale);
                int endX = Math.max(startX + 1, (int) Math.ceil((worldX + 1 - minX) * scale));
                int startY = (int) Math.floor((worldZ - minZ) * scale);
                int endY = Math.max(startY + 1, (int) Math.ceil((worldZ + 1 - minZ) * scale));
                if (endX <= 0 || endY <= 0 || startX >= image.getWidth() || startY >= image.getHeight()) {
                    return;
                }
                startX = Math.max(0, startX);
                startY = Math.max(0, startY);
                endX = Math.min(image.getWidth(), endX);
                endY = Math.min(image.getHeight(), endY);
                graphics.setColor(palette.color(classification.tier()));
                graphics.fillRect(startX, startY, endX - startX, endY - startY);
                drawn[0]++;
            });
            if (drawn[0] > 0) {
                drawLegend(graphics, image);
            }
        } finally {
            graphics.dispose();
        }
        progress.done("Soil fertility drawn");
        return drawn[0];
    }

    private void drawLegend(Graphics2D graphics, BufferedImage image) {
        if (image.getWidth() < LEGEND_MIN_WIDTH
                || image.getHeight() < LEGEND_MIN_HEIGHT) {
            return;
        }

        int titleHeight = 18;
        int height = 8 + titleHeight + SoilFertilityTier.values().length * LINE_HEIGHT;
        int x = image.getWidth() - LEGEND_WIDTH - 8;
        int y = 8;

        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        graphics.setColor(new Color(0, 0, 0, 165));
        graphics.fillRect(x, y, LEGEND_WIDTH, height);

        graphics.setColor(Color.WHITE);
        graphics.drawString("Nominal Fertility", x + 6, y + 13);

        int line = 0;
        for (SoilFertilityTier tier : SoilFertilityTier.values()) {
            int rowY = y + titleHeight + 4 + line * LINE_HEIGHT;
            graphics.setColor(palette.color(tier));
            graphics.fillRect(x + 6, rowY, 10, 10);

            graphics.setColor(Color.WHITE);
            graphics.drawString(
                    label(tier) + " " + tier.fertilityPercent() + "%",
                    x + 22,
                    rowY + 10
            );
            line++;
        }
    }

    private String label(SoilFertilityTier tier) {
        return switch (tier) {
            case BONY -> "Bony";
            case BARREN -> "Barren";
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
            case TERRA_PRETA -> "Terra Preta";
        };
    }

    private SoilFertilityClassification directlyVisibleFertility(SurfaceBlock block) {
        if (block == null
                || block.surfaceClass() == SurfaceClass.WATER
                || block.surfaceClass() == SurfaceClass.SNOW) {
            return null;
        }
        return classifier.classify(block.blockInfo()).orElse(null);
    }
}
