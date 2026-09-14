package cartographer.resource;

/** Marker for the two semantically distinct surface render analyses. */
public sealed interface SurfaceRenderAnalysis
        permits SurfaceMaterialAnalysis, SurfaceObjectSelectionAnalysis {
}
