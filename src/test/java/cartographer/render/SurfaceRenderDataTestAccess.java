package cartographer.render;

/** Test-only bridge for package-private Surface render sampling inspection. */
public final class SurfaceRenderDataTestAccess {
    private SurfaceRenderDataTestAccess() {
    }

    public static boolean hasSurfaceAt(
            SurfaceRenderData data,
            int imageX,
            int imageY
    ) {
        return data.hasSurfaceAt(imageX, imageY);
    }

    public static int surfaceWorldXAt(
            SurfaceRenderData data,
            int imageX,
            int imageY
    ) {
        return data.surfaceWorldXAt(imageX, imageY);
    }

}
