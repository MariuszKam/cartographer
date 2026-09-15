package cartographer.resource;

import cartographer.model.BlockInfo;

import java.util.List;
import java.util.Map;

/** Resolves discovered object observations into an exact render analysis. */
public final class SurfaceObjectAnalyzer {
    public SurfaceObjectAnalysis analyze(
            ObservedSurfaceResource resource,
            Map<Integer, BlockInfo> registry
    ) {
        List<SurfaceResourcePoint> occurrences = resource.observations().stream()
                .map(observation -> {
                    BlockInfo block = registry.get(observation.blockId());
                    if (block == null) {
                        throw new IllegalStateException(
                                "Discovery observation references missing block ID: "
                                        + observation.blockId());
                    }
                    return new SurfaceResourcePoint(
                            observation.worldX(), observation.worldY(), observation.worldZ(),
                            block.code());
                })
                .toList();
        return new SurfaceObjectAnalysis(
                SurfaceObjectPresentation.displayName(resource.candidate()),
                SurfaceObjectPresentation.qualifiedResourceKey(resource.candidate()),
                resource.candidate().blockIds().size(),
                occurrences,
                resource.candidate().families()
        );
    }
}
