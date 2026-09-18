package cartographer.ui.workstation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationOperationSnapshot(
        long generation,
        WorkstationOperationScope scope,
        String type,
        String requestSummary,
        WorkstationOperationState state,
        String stage,
        double progress,
        boolean resultDelivered,
        Optional<String> failureMessage
) {
    public WorkstationOperationSnapshot {
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        scope = Objects.requireNonNull(scope, "scope is required");
        type = Objects.requireNonNull(type, "type is required");
        requestSummary = Objects.requireNonNull(requestSummary, "requestSummary is required");
        state = Objects.requireNonNull(state, "state is required");
        stage = stage == null ? "" : stage;
        failureMessage = Objects.requireNonNull(
                failureMessage,
                "failureMessage is required"
        );
    }
}
