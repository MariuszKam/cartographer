package cartographer.resource;

import java.util.Set;

public record SurfaceResourceDeposit(
        String query,
        int blockCount,
        Set<String> blockCodes,
        int minWorldX,
        int maxWorldX,
        int minWorldZ,
        int maxWorldZ,
        int minY,
        int maxY,
        double centerWorldX,
        double centerWorldZ
) {
    public SurfaceResourceDeposit {
        if (query == null
                || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Surface resource query is required"
            );
        }

        if (blockCount <= 0) {
            throw new IllegalArgumentException(
                    "Surface resource deposit must contain blocks"
            );
        }

        blockCodes =
                blockCodes == null
                        ? Set.of()
                        : Set.copyOf(
                        blockCodes
                );
    }

    public int widthBlocks() {
        return maxWorldX
                - minWorldX
                + 1;
    }

    public int depthBlocks() {
        return maxWorldZ
                - minWorldZ
                + 1;
    }
}