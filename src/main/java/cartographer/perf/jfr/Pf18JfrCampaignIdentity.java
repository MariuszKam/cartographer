package cartographer.perf.jfr;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Review identity persisted with a separate PF-1.8 JFR summary. */
public record Pf18JfrCampaignIdentity(
        String gitSha,
        String workloadId,
        String workloadFamily,
        int radius,
        String declaredProfileState,
        String jfrConfiguration,
        long maxRecordingSizeBytes,
        Path sourcePath,
        String sourceSafetyStatus,
        Optional<String> semanticFingerprint,
        Optional<String> imageFingerprint
) {
    public Pf18JfrCampaignIdentity {
        gitSha = required(gitSha, "gitSha");
        workloadId = required(workloadId, "workloadId");
        workloadFamily = required(workloadFamily, "workloadFamily");
        declaredProfileState = required(declaredProfileState, "declaredProfileState");
        jfrConfiguration = required(jfrConfiguration, "jfrConfiguration");
        sourceSafetyStatus = required(sourceSafetyStatus, "sourceSafetyStatus");
        if (radius <= 0 || maxRecordingSizeBytes <= 0) {
            throw new IllegalArgumentException("invalid JFR campaign identity values");
        }
        sourcePath = Objects.requireNonNull(sourcePath).toAbsolutePath().normalize();
        semanticFingerprint = Objects.requireNonNull(semanticFingerprint);
        imageFingerprint = Objects.requireNonNull(imageFingerprint);
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
