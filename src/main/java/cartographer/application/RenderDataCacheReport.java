package cartographer.application;

import java.util.List;

/** Immutable per-render report for optional PF-1.7 render-data caching. */
public record RenderDataCacheReport(
        boolean enabled,
        ArtifactStats terrain,
        ArtifactStats surface,
        List<String> notes
) {
    public RenderDataCacheReport {
        terrain = terrain == null ? ArtifactStats.empty() : terrain;
        surface = surface == null ? ArtifactStats.empty() : surface;
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    public static RenderDataCacheReport disabled(String note) {
        return new RenderDataCacheReport(false, ArtifactStats.empty(), ArtifactStats.empty(),
                note == null ? List.of() : List.of(note));
    }

    public record ArtifactStats(
            int requested,
            int hits,
            int misses,
            int corruptOrIncompatible,
            int sourceLoaded,
            int published,
            int skippedIncompleteForPublish,
            int worldMismatches
    ) {
        public ArtifactStats {
            if (requested < 0 || hits < 0 || misses < 0 || corruptOrIncompatible < 0
                    || sourceLoaded < 0 || published < 0 || skippedIncompleteForPublish < 0
                    || worldMismatches < 0) {
                throw new IllegalArgumentException("cache report counters cannot be negative");
            }
        }

        public static ArtifactStats empty() {
            return new ArtifactStats(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
