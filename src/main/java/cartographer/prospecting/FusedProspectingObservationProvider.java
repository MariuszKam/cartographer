package cartographer.prospecting;

import cartographer.model.WorldPosition;
import cartographer.save.SaveSession;

import java.nio.file.Path;
import java.util.List;

/** Prospecting provider contract backed by one fused ROCK/resource analysis. */
public interface FusedProspectingObservationProvider {
    FusedProspectingResult analyze(
            SaveSession session,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    );

    FusedProspectingResult analyze(
            Path savePath,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    );
}
