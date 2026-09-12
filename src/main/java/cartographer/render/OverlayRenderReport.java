package cartographer.render;

public record OverlayRenderReport(
        int candidates,
        int drawn,
        int unavailable
) {
    public static OverlayRenderReport none() {
        return new OverlayRenderReport(
                0,
                0,
                0
        );
    }
}