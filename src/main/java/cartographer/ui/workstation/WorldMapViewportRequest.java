package cartographer.ui.workstation;

import java.util.Objects;

/** Current viewport demand plus the subset that still needs materialization. */
public record WorldMapViewportRequest(
        WorldMapViewportDemand active,
        WorldMapViewportDemand missing
) {
    public WorldMapViewportRequest {
        Objects.requireNonNull(active, "active is required");
        Objects.requireNonNull(missing, "missing is required");
        if (active.lod() != missing.lod()) {
            throw new IllegalArgumentException(
                    "active and missing demand must use the same LOD"
            );
        }
    }
}
