package cartographer.prospecting;

import cartographer.model.WorldPosition;

import java.nio.file.Path;
import java.util.List;

/** Capability implemented by providers that can share one decoded chunk stream. */
public interface FusedProspectingObservationProvider {
    FusedProspectingResult analyze(
            Path savePath,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    );
}
