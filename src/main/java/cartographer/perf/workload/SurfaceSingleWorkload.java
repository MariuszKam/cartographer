package cartographer.perf.workload;

import java.util.Objects;

public record SurfaceSingleWorkload(
        RadiusProfile radius,
        ResourceIdentity resource
) implements WorkloadSpec {
    public SurfaceSingleWorkload {
        Objects.requireNonNull(radius, "radius is required");
        Objects.requireNonNull(resource, "resource is required");
    }

    @Override
    public WorkloadFamily family() {
        return WorkloadFamily.SURFACE_SINGLE;
    }

    @Override
    public String id() {
        return "SURFACE_SINGLE_" + resource.value().replace(' ', '_')
                + "_R" + radius.blocks();
    }
}
