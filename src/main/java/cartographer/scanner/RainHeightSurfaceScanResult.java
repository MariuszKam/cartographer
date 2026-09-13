package cartographer.scanner;

import cartographer.model.SurfaceBlock;

import java.util.List;
import java.util.Objects;

public record RainHeightSurfaceScanResult(
        List<SurfaceBlock> blocks,
        List<RainHeightSurfaceTarget> unresolvedTargets
) {
    public RainHeightSurfaceScanResult {
        blocks = copyRequired("blocks", blocks);
        unresolvedTargets = copyRequired("unresolvedTargets", unresolvedTargets);
    }

    private static <T> List<T> copyRequired(String name, List<T> values) {
        Objects.requireNonNull(values, name + " is required");
        for (T value : values) {
            Objects.requireNonNull(value, name + " cannot contain null");
        }
        return List.copyOf(values);
    }
}
