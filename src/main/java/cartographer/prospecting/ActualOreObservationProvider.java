package cartographer.prospecting;

import cartographer.model.WorldPosition;

import java.nio.file.Path;

@FunctionalInterface
public interface ActualOreObservationProvider {
    boolean observed(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    );

    default ActualOreObservation observation(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    ) {
        return observed(resourceKey, savePath, center, radius)
                ? ActualOreObservation.OBSERVED
                : ActualOreObservation.NOT_OBSERVED;
    }

    static ActualOreObservationProvider none() {
        return (resourceKey, savePath, center, radius) -> false;
    }
}
