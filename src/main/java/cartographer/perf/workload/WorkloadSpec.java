package cartographer.perf.workload;

public sealed interface WorkloadSpec permits
        MapWorkload,
        OreSingleWorkload,
        SurfaceSingleWorkload,
        RockAtYWorkload,
        RockUpperWorkload,
        ProspectingWorkload {
    WorkloadFamily family();

    RadiusProfile radius();

    String id();
}
