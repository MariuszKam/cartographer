package cartographer.model;

import java.util.Objects;

/** Stable, explicit persistence codes for {@link SurfaceClass}. */
public final class SurfaceClassCode {
    private SurfaceClassCode() {
    }

    public static byte encode(SurfaceClass value) {
        return switch (Objects.requireNonNull(value, "surface class is required")) {
            case WATER -> 0;
            case GRASS -> 1;
            case FOREST_FLOOR -> 2;
            case SOIL -> 3;
            case ROCK -> 4;
            case SAND -> 5;
            case GRAVEL -> 6;
            case VEGETATION -> 7;
            case SNOW -> 8;
            case UNKNOWN -> 9;
        };
    }

    public static SurfaceClass decode(byte code) {
        return switch (code) {
            case 0 -> SurfaceClass.WATER;
            case 1 -> SurfaceClass.GRASS;
            case 2 -> SurfaceClass.FOREST_FLOOR;
            case 3 -> SurfaceClass.SOIL;
            case 4 -> SurfaceClass.ROCK;
            case 5 -> SurfaceClass.SAND;
            case 6 -> SurfaceClass.GRAVEL;
            case 7 -> SurfaceClass.VEGETATION;
            case 8 -> SurfaceClass.SNOW;
            case 9 -> SurfaceClass.UNKNOWN;
            default -> throw new IllegalArgumentException("invalid surface class code: " + code);
        };
    }
}
