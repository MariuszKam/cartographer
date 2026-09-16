package cartographer.perf.workload;

import java.util.Objects;

public record ProspectingWorkload(
        RadiusProfile radius,
        ProspectingStrategy strategy
) implements WorkloadSpec {
    public ProspectingWorkload {
        Objects.requireNonNull(radius, "radius is required");
        Objects.requireNonNull(strategy, "strategy is required");
    }

    @Override
    public WorkloadFamily family() {
        return strategy == ProspectingStrategy.SELECTED_RESOURCE_SET
                ? WorkloadFamily.PROSPECTING_SMALL
                : WorkloadFamily.PROSPECTING_FULL;
    }

    @Override
    public String id() {
        return family().name() + "_R" + radius.blocks();
    }
}
