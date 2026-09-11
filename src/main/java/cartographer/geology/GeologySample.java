package cartographer.geology;

import cartographer.model.SurfaceBlock;

public record GeologySample(SurfaceBlock block, String rockFamily, boolean geological) {
}
