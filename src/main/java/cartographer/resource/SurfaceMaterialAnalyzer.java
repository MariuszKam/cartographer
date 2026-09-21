package cartographer.resource;

import cartographer.application.SurfaceMaterialMatch;
import cartographer.scanner.SurfaceMapScanResult;

/** Material-specific facade over the compact Surface clustering algorithm. */
public final class SurfaceMaterialAnalyzer {
    private final SurfaceResourceAnalyzer delegate =
            new SurfaceResourceAnalyzer();

    public SurfaceMaterialAnalysis analyze(
            SurfaceMapScanResult surface,
            SurfaceMaterialMatch match,
            String displayName
    ) {
        if (surface == null || match == null) {
            throw new IllegalArgumentException(
                    "Surface result and material match are required"
            );
        }
        return convert(delegate.analyze(
                surface,
                match.requiredTokens(),
                displayName
        ));
    }

    private SurfaceMaterialAnalysis convert(
            SurfaceResourceAnalysis analysis
    ) {
        return new SurfaceMaterialAnalysis(
                analysis.query(),
                analysis.surfaceColumns(),
                analysis.matchingBlocks(),
                analysis.deposits().stream()
                        .map(deposit -> new SurfaceMaterialDeposit(
                                deposit.query(),
                                deposit.blockCount(),
                                deposit.blockCodes(),
                                deposit.minWorldX(),
                                deposit.maxWorldX(),
                                deposit.minWorldZ(),
                                deposit.maxWorldZ(),
                                deposit.minY(),
                                deposit.maxY(),
                                deposit.centerWorldX(),
                                deposit.centerWorldZ()
                        ))
                        .toList()
        );
    }
}
