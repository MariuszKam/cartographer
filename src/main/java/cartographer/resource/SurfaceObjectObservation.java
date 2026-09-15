package cartographer.resource;

import java.util.Objects;

/** A physical loose surface-object occurrence found at a world position. */
public record SurfaceObjectObservation(
        SurfaceObjectCandidate candidate,
        int worldX,
        int worldY,
        int worldZ,
        int blockId
) {
    public SurfaceObjectObservation {
        Objects.requireNonNull(candidate, "candidate is required");
    }
}
