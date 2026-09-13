package cartographer.geology.rock;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record RockColumnSample(
        int worldX,
        int worldZ,
        RockColumnState state,
        Optional<RockIdentity> rock,
        OptionalInt rockY
) {
    public RockColumnSample {
        Objects.requireNonNull(state, "Rock column state is required");
        Objects.requireNonNull(rock, "Rock identity is required");
        Objects.requireNonNull(rockY, "Rock Y is required");

        if (state == RockColumnState.OBSERVED
                && (rock.isEmpty() || rockY.isEmpty())) {
            throw new IllegalArgumentException(
                    "Observed rock columns require a rock and Y coordinate"
            );
        }
        if (state != RockColumnState.OBSERVED
                && (rock.isPresent() || rockY.isPresent())) {
            throw new IllegalArgumentException(
                    "Only observed rock columns may contain rock data"
            );
        }
    }

    public static RockColumnSample observed(
            int worldX,
            int worldZ,
            RockIdentity rock,
            int rockY
    ) {
        return new RockColumnSample(
                worldX,
                worldZ,
                RockColumnState.OBSERVED,
                Optional.of(Objects.requireNonNull(rock, "rock is required")),
                OptionalInt.of(rockY)
        );
    }

    public static RockColumnSample noRock(int worldX, int worldZ) {
        return unavailableOrEmpty(worldX, worldZ, RockColumnState.NO_ROCK);
    }

    public static RockColumnSample unavailable(int worldX, int worldZ) {
        return unavailableOrEmpty(worldX, worldZ, RockColumnState.UNAVAILABLE);
    }

    private static RockColumnSample unavailableOrEmpty(
            int worldX,
            int worldZ,
            RockColumnState state
    ) {
        return new RockColumnSample(
                worldX,
                worldZ,
                state,
                Optional.empty(),
                OptionalInt.empty()
        );
    }
}
