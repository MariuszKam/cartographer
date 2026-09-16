package cartographer.perf.workload;

import java.util.Objects;

public record OreSingleWorkload(
        RadiusProfile radius,
        ResourceIdentity resource
) implements WorkloadSpec {
    public OreSingleWorkload {
        Objects.requireNonNull(radius, "radius is required");
        Objects.requireNonNull(resource, "resource is required");
    }

    @Override
    public WorkloadFamily family() {
        return WorkloadFamily.ORE_SINGLE;
    }

    @Override
    public String id() {
        return "ORE_SINGLE_" + idPart(resource) + "_R" + radius.blocks();
    }

    private static String idPart(ResourceIdentity resource) {
        return resource.value().replace(' ', '_');
    }
}
