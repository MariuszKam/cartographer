package cartographer.render;

import cartographer.resource.SurfaceObjectFamily;

import java.util.Collection;
import java.util.Objects;

/** Pure visual policy for family-aware surface-object markers. */
public final class SurfaceObjectMarkerStylePolicy {
    public SurfaceObjectMarkerStyle forFamilies(Collection<SurfaceObjectFamily> families) {
        Objects.requireNonNull(families, "families are required");
        if (families.isEmpty()) {
            throw new IllegalArgumentException("at least one family is required");
        }
        if (families.size() > 1) {
            return new SurfaceObjectMarkerStyle(SurfaceObjectMarkerShape.MIXED, 4);
        }
        return switch (families.iterator().next()) {
            case ORE_BITS -> new SurfaceObjectMarkerStyle(SurfaceObjectMarkerShape.DIAMOND, 4);
            case FLINT -> new SurfaceObjectMarkerStyle(SurfaceObjectMarkerShape.TRIANGLE, 4);
            case LOOSE_STONE -> new SurfaceObjectMarkerStyle(SurfaceObjectMarkerShape.CIRCLE, 4);
            case LOOSE_BOULDER -> new SurfaceObjectMarkerStyle(SurfaceObjectMarkerShape.SQUARE, 5);
        };
    }
}
