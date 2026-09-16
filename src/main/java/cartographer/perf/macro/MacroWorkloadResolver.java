package cartographer.perf.macro;

import cartographer.perf.workload.RadiusProfile;
import cartographer.perf.workload.RockUpperWorkload;
import cartographer.perf.workload.WorkloadSpec;

/** Resolves the supported ROCK macro-baseline scaling ladder. */
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
            case "ROCK_UPPER_R1024" -> new RockUpperWorkload(RadiusProfile.R1024);
            case "ROCK_UPPER_R2048" -> new RockUpperWorkload(RadiusProfile.R2048);
            case "ROCK_UPPER_R4096" -> new RockUpperWorkload(RadiusProfile.R4096);
            default -> throw new IllegalArgumentException(
                    "Unsupported macro workload: " + workloadId
            );
        };
    }
}
