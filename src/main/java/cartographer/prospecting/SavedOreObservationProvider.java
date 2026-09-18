package cartographer.prospecting;

import cartographer.model.WorldPosition;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class SavedOreObservationProvider implements ActualOreObservationProvider, FusedProspectingObservationProvider {
    private final FusedProspectingEngine fusedEngine;

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this.fusedEngine = new FusedProspectingEngine(
                Objects.requireNonNull(reader, "reader is required"),
                Objects.requireNonNull(
                        metadataReader,
                        "metadata reader is required"
                )
        );
    }

    @Override
    public FusedProspectingResult analyze(
            Path savePath,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    ) {
        return fusedEngine.analyze(savePath, center, radius, resourceKeys);
    }

    @Override
    public boolean observed(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    ) {
        return observation(resourceKey, savePath, center, radius)
                == ActualOreObservation.OBSERVED;
    }

    @Override
    public ActualOreObservation observation(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    ) {
        return fusedEngine.analyze(savePath, center, radius, List.of(resourceKey))
                .observation(resourceKey);
    }
}
