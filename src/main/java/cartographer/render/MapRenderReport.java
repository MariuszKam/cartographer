package cartographer.render;

public record MapRenderReport(
        int width,
        int height,
        int chunks,
        int tilesDrawn,
        int markerCount,
        RenderStyle style,
        String layers) {
}
