package cartographer.application;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMap;
import cartographer.model.WorldPosition;
import cartographer.render.RockMapRenderResult;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;

import java.util.Objects;
import java.util.Optional;

public record RenderRockMapResult(
        Optional<RockMap> retainedMap,
        RockMapRenderResult rendered,
        RockCatalog catalog,
        SelectiveChunkStreamStats chunkStats,
        ReadDiagnostics diagnostics,
        WorldPosition center,
        int minY,
        int maxYExclusive
) {
    public RenderRockMapResult {
        retainedMap = Objects.requireNonNull(
                retainedMap,
                "retained rock map option is required"
        );
        Objects.requireNonNull(rendered, "rendered rock map is required");
        Objects.requireNonNull(catalog, "rock catalog is required");
        Objects.requireNonNull(chunkStats, "chunk stats are required");
        Objects.requireNonNull(diagnostics, "diagnostics are required");
        Objects.requireNonNull(center, "center is required");
    }
}
