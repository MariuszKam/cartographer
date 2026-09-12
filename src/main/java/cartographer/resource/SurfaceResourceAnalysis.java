package cartographer.resource;

import java.util.List;

public record SurfaceResourceAnalysis(
        String query,
        int surfaceColumns,
        List<SurfaceResourcePoint> matchingBlocks,
        List<SurfaceResourceDeposit> deposits
) {
    public SurfaceResourceAnalysis {
        if (query == null
                || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Surface resource query is required"
            );
        }

        matchingBlocks =
                matchingBlocks == null
                        ? List.of()
                        : List.copyOf(
                        matchingBlocks
                );

        deposits =
                deposits == null
                        ? List.of()
                        : List.copyOf(
                        deposits
                );
    }

    public int matchingBlockCount() {
        return matchingBlocks.size();
    }

    public int depositCount() {
        return deposits.size();
    }
}