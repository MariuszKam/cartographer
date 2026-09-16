package cartographer.perf.macro;

import cartographer.perf.workload.RadiusProfile;
import cartographer.perf.workload.RockUpperWorkload;
import cartographer.perf.workload.WorkloadSpec;

/** Resolves the deliberately small first macro-baseline workload set. */
public final class MacroWorkloadResolver {
    private MacroWorkloadResolver() {
    }

    public static WorkloadSpec resolve(String workloadId) {
        if (workloadId == null || workloadId.isBlank()) {
            throw new IllegalArgumentException("workload must not be blank");
        }
        return switch (workloadId.trim()) {
            case "ROCK_UPPER_R256" -> new RockUpperWorkload(RadiusProfile.R256);
            case "ROCK_UPPER_R512" -> new RockUpperWorkload(RadiusProfile.R512);
            default -> throw new IllegalArgumentException(
                    "Unsupported macro workload: " + workloadId
            );
        };
    }
}
