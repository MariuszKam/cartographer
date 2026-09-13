package cartographer.render;

import cartographer.geology.rock.RockIdentity;

import java.util.Objects;

public record RockLegendEntry(
        RockIdentity rock,
        int argb,
        long observedCellCount,
        double observedPercentage
) {
    public RockLegendEntry {
        Objects.requireNonNull(rock, "legend rock is required");
        if (observedCellCount <= 0) {
            throw new IllegalArgumentException(
                    "legend observed cell count must be positive"
            );
        }
        if (observedPercentage < 0.0 || observedPercentage > 100.0) {
            throw new IllegalArgumentException(
                    "legend observed percentage must be between 0 and 100"
            );
        }
    }
}
