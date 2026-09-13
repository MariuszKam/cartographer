package cartographer.application;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMap;
import cartographer.model.WorldPosition;
import cartographer.render.RockMapRenderResult;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;

import java.util.Objects;

public record RenderRockMapResult(
        RockMap map,
        RockMapRenderResult rendered,
        RockCatalog catalog,
        SelectiveChunkStreamStats chunkStats,
        ReadDiagnostics diagnostics,
        WorldPosition center,
        int minY,
        int maxYExclusive
) {
    public RenderRockMapResult {
        Objects.requireNonNull(map, "rock map is required");
        Objects.requireNonNull(rendered, "rendered rock map is required");
        Objects.requireNonNull(catalog, "rock catalog is required");
        Objects.requireNonNull(chunkStats, "chunk stats are required");
        Objects.requireNonNull(diagnostics, "diagnostics are required");
        Objects.requireNonNull(center, "center is required");
    }
}
