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
        String displayName = resource.candidate().namespace().equals("game")
                || resource.candidate().namespace().isBlank()
                ? resource.candidate().displayName()
                : resource.candidate().displayName() + " ["
                        + resource.candidate().namespace() + "]";
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
                displayName,
                resource.candidate().qualifiedResourceKey(),
                resource.candidate().blockIds().size(),
                occurrences
        );
    }
}
