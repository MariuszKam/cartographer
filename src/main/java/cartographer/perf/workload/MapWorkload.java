package cartographer.perf.workload;

import java.util.Objects;

public record MapWorkload(RadiusProfile radius) implements WorkloadSpec {
    public MapWorkload {
        Objects.requireNonNull(radius, "radius is required");
    }

    @Override
    public WorkloadFamily family() {
        return WorkloadFamily.MAP;
    }

    @Override
    public String id() {
        return "MAP_R" + radius.blocks();
    }
}
