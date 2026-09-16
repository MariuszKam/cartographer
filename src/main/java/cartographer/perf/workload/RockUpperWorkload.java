package cartographer.perf.workload;

import java.util.Objects;

public record RockUpperWorkload(RadiusProfile radius) implements WorkloadSpec {
    public RockUpperWorkload {
        Objects.requireNonNull(radius, "radius is required");
    }

    @Override
    public WorkloadFamily family() {
        return WorkloadFamily.ROCK_UPPER;
    }

    @Override
    public String id() {
        return "ROCK_UPPER_R" + radius.blocks();
    }
}
