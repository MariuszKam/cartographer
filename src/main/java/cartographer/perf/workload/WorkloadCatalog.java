package cartographer.perf.workload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit deterministic catalog of current semantic benchmark workload shapes. */
public final class WorkloadCatalog {
    private static final List<RadiusProfile> RADII = List.of(
            RadiusProfile.R128,
            RadiusProfile.R256,
            RadiusProfile.R512,
            RadiusProfile.R1024
    );

    private WorkloadCatalog() {
    }

    /**
     * Builds the standard catalog using an explicit resource and absolute Y.
     * The resource is used by both resource-specific families; the Y value is
     * used only by ROCK AT Y. No save or player state is consulted.
     */
    public static List<WorkloadSpec> standard(
            ResourceIdentity resource,
            List<ResourceIdentity> selectedProspectingResources,
            int absoluteWorldY
    ) {
        Objects.requireNonNull(resource, "resource is required");
        Objects.requireNonNull(
                selectedProspectingResources,
                "selectedProspectingResources is required"
        );
        List<WorkloadSpec> workloads = new ArrayList<>();
        for (RadiusProfile radius : RADII) {
            workloads.add(new MapWorkload(radius));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new OreSingleWorkload(radius, resource));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new SurfaceSingleWorkload(radius, resource));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new RockAtYWorkload(radius, absoluteWorldY));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new RockUpperWorkload(radius));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new ProspectingWorkload(
                    radius,
                    ProspectingStrategy.SELECTED_RESOURCE_SET,
                    selectedProspectingResources
            ));
        }
        for (RadiusProfile radius : RADII) {
            workloads.add(new ProspectingWorkload(
                    radius,
                    ProspectingStrategy.ALL_DISCOVERED_SUPPORTED_RESOURCES,
                    List.of()
            ));
        }
        return List.copyOf(workloads);
    }
}
