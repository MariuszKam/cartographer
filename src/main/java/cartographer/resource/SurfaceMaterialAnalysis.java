package cartographer.resource;

import java.util.List;

/** Analysis of terrain/surface material blocks and their connected areas. */
public record SurfaceMaterialAnalysis(
        String materialName,
        int totalSurfaceColumns,
        List<SurfaceResourcePoint> matchingBlocks,
        List<SurfaceMaterialDeposit> deposits
) implements SurfaceRenderAnalysis {
    public SurfaceMaterialAnalysis {
        if (materialName == null || materialName.isBlank()) {
            throw new IllegalArgumentException("Surface material name is required");
        }
        if (totalSurfaceColumns < 0) {
            throw new IllegalArgumentException("Surface material column count must not be negative");
        }
        matchingBlocks = matchingBlocks == null ? List.of() : List.copyOf(matchingBlocks);
        deposits = deposits == null ? List.of() : List.copyOf(deposits);
    }

    public int matchedBlockCount() { return matchingBlocks.size(); }
    public int depositCount() { return deposits.size(); }
}
