package cartographer.application;

/**
 * Declares how much Surface state a prepared-map consumer actually needs.
 *
 * <p>RENDER permits render-sized derived state. ANALYSIS requires the exact
 * request-shaped Surface result because downstream analysis may inspect every
 * resolved world column.</p>
 */
public enum SurfaceDataRequirement {
    NONE,
    RENDER,
    ANALYSIS;

    public boolean requiresSurface() {
        return this != NONE;
    }

    public boolean requiresAnalysis() {
        return this == ANALYSIS;
    }
}
