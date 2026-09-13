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

    static ActualOreObservationProvider none() {
        return (resourceKey, savePath, center, radius) -> false;
    }
}
