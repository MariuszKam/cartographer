package cartographer.model;

public record SurfaceBlock(
        int worldX,
        int y,
        int worldZ,
        BlockInfo blockInfo,
        int liquidBlockId,
        BlockInfo liquidBlockInfo,
        SurfaceClass surfaceClass
) {
    public SurfaceBlock(
            int worldX,
            int y,
            int worldZ,
            BlockInfo blockInfo
    ) {
        this(
                worldX,
                y,
                worldZ,
                blockInfo,
                0,
                BlockInfo.unknown(0),
                SurfaceClass.UNKNOWN
        );
    }
}