package cartographer.application;

import cartographer.geology.rock.RockMapMode;
import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record RenderRockMapRequest(
        Path savePath,
        RockMapMode mode,
        int radius,
        Optional<WorldPosition> center,
        OptionalInt y,
        OptionalInt minY,
        OptionalInt maxYExclusive
) {
    public RenderRockMapRequest {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(mode, "rock map mode is required");
        Objects.requireNonNull(center, "center is required");
        Objects.requireNonNull(y, "y is required");
        Objects.requireNonNull(minY, "minY is required");
        Objects.requireNonNull(maxYExclusive, "maxYExclusive is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (mode == RockMapMode.AT_Y && y.isEmpty()) {
            throw new IllegalArgumentException("AT_Y mode requires y");
        }
        if (mode == RockMapMode.UPPER_ROCK && y.isPresent()) {
            throw new IllegalArgumentException("y is only valid for AT_Y mode");
        }
        if (minY.isPresent() && maxYExclusive.isPresent()
                && minY.getAsInt() >= maxYExclusive.getAsInt()) {
            throw new IllegalArgumentException("minY must be less than maxYExclusive");
        }
    }
}
