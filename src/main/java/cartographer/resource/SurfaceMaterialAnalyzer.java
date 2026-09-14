package cartographer.resource;

import cartographer.model.SurfaceBlock;

import java.util.List;

/** Material-specific facade over the existing material clustering algorithm. */
public final class SurfaceMaterialAnalyzer {
    private final SurfaceResourceAnalyzer delegate = new SurfaceResourceAnalyzer();

    public SurfaceMaterialAnalysis analyze(List<SurfaceBlock> blocks, String query) {
        return convert(delegate.analyze(blocks, query));
    }

    public SurfaceMaterialAnalysis analyzeMatched(
            String displayName,
            List<SurfaceBlock> matchingBlocks,
            int totalSurfaceColumns
    ) {
        return convert(delegate.analyzeMatched(displayName, matchingBlocks, totalSurfaceColumns));
    }

    private SurfaceMaterialAnalysis convert(SurfaceResourceAnalysis analysis) {
        return new SurfaceMaterialAnalysis(
                analysis.query(),
                analysis.surfaceColumns(),
                analysis.matchingBlocks(),
                analysis.deposits().stream()
                        .map(deposit -> new SurfaceMaterialDeposit(
                                deposit.query(), deposit.blockCount(), deposit.blockCodes(),
                                deposit.minWorldX(), deposit.maxWorldX(),
                                deposit.minWorldZ(), deposit.maxWorldZ(),
                                deposit.minY(), deposit.maxY(),
                                deposit.centerWorldX(), deposit.centerWorldZ()))
                        .toList()
        );
    }
}
