package cartographer.render;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;

import java.util.Objects;
import java.util.Optional;

/** Shared ROCK pixel-color semantics for retained and snapshot-direct paths. */
final class RockRenderColors {
    private static final int NO_ROCK_COLOR = 0xFF4A4A4A;
    private static final int UNAVAILABLE_LIGHT = 0xFF888888;
    private static final int UNAVAILABLE_DARK = 0xFF707070;

    private final RockPalette palette;

    RockRenderColors(RockPalette palette) {
        this.palette = Objects.requireNonNull(
                palette,
                "rock palette is required"
        );
    }

    int color(
            RockColumnState state,
            RockIdentity identity,
            int worldX,
            int worldZ,
            Optional<String> highlightRockCode
    ) {
        Objects.requireNonNull(state, "rock state is required");
        highlightRockCode = Objects.requireNonNull(
                highlightRockCode,
                "highlight rock code is required"
        );
        return switch (state) {
            case OBSERVED -> {
                RockIdentity rock = Objects.requireNonNull(
                        identity,
                        "observed rock identity is required"
                );
                int color = palette.colorFor(rock);
                if (highlightRockCode.isPresent()
                        && !highlightRockCode.orElseThrow()
                        .equals(rock.code())) {
                    color = dim(color);
                }
                yield color;
            }
            case NO_ROCK -> NO_ROCK_COLOR;
            case UNAVAILABLE -> ((worldX + worldZ) & 1) == 0
                    ? UNAVAILABLE_LIGHT
                    : UNAVAILABLE_DARK;
        };
    }

    private static int dim(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        int red = ((argb >>> 16) & 0xff) / 4;
        int green = ((argb >>> 8) & 0xff) / 4;
        int blue = (argb & 0xff) / 4;
        return (alpha << 24)
                | (red << 16)
                | (green << 8)
                | blue;
    }
}
