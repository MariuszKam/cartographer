package cartographer.perf.workload;

import java.util.Objects;

/** ROCK AT Y uses an explicit absolute world Y coordinate. */
public record RockAtYWorkload(
        RadiusProfile radius,
        int absoluteWorldY
) implements WorkloadSpec {
    public RockAtYWorkload {
        Objects.requireNonNull(radius, "radius is required");
    }

    @Override
    public WorkloadFamily family() {
        return WorkloadFamily.ROCK_AT_Y;
    }

    @Override
    public String id() {
        return "ROCK_AT_Y_Y" + absoluteWorldY + "_R" + radius.blocks();
    }
}
