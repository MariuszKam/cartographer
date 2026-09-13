package cartographer.resource;

import cartographer.model.SurfaceBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SurfaceResourceAnalyzer {

    public SurfaceResourceAnalysis analyze(
            List<SurfaceBlock> surfaceBlocks,
            String query
    ) {
        String normalizedQuery =
                normalize(
                        query
                );

        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "Surface resource query is required"
            );
        }

        List<SurfaceBlock> matchingBlocks =
                surfaceBlocks.stream()
                        .filter(block -> block.blockInfo() != null
                                && block.blockInfo().code() != null
                                && normalize(block.blockInfo().code()).contains(normalizedQuery))
                        .toList();
        return analyzeMatched(
                normalizedQuery,
                matchingBlocks,
                surfaceBlocks.size()
        );
    }

    public SurfaceResourceAnalysis analyzeMatched(
            String displayName,
            List<SurfaceBlock> matchingBlocks,
            int totalSurfaceColumns
    ) {
        String normalizedName = normalize(displayName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Surface resource display name is required"
            );
        }
        if (totalSurfaceColumns < 0) {
            throw new IllegalArgumentException(
                    "Surface resource column count must not be negative"
            );
        }

        List<SurfaceResourcePoint> matching =
                new ArrayList<>();

        Map<Long, SurfaceResourcePoint> remaining =
                new HashMap<>();

        for (SurfaceBlock block : matchingBlocks) {
            if (block.blockInfo() == null
                    || block.blockInfo().code() == null) {

                continue;
            }

            String blockCode =
                    block.blockInfo()
                            .code();

            SurfaceResourcePoint point =
                    new SurfaceResourcePoint(
                            block.worldX(),
                            block.y(),
                            block.worldZ(),
                            blockCode
                    );

            matching.add(
                    point
            );

            remaining.put(
                    coordinateKey(
                            point.worldX(),
                            point.worldZ()
                    ),
                    point
            );
        }

        List<SurfaceResourceDeposit> deposits =
                new ArrayList<>();

        while (!remaining.isEmpty()) {
            SurfaceResourcePoint start =
                    remaining.values()
                            .iterator()
                            .next();

            remaining.remove(
                    coordinateKey(
                            start.worldX(),
                            start.worldZ()
                    )
            );

            deposits.add(
                    collectDeposit(
                            normalizedName,
                            start,
                            remaining
                    )
            );
        }

        deposits.sort(
                Comparator.comparingInt(
                                SurfaceResourceDeposit::blockCount
                        )
                        .reversed()
                        .thenComparingDouble(
                                SurfaceResourceDeposit::centerWorldX
                        )
                        .thenComparingDouble(
                                SurfaceResourceDeposit::centerWorldZ
                        )
        );

        return new SurfaceResourceAnalysis(
                normalizedName,
                totalSurfaceColumns,
                List.copyOf(
                        matching
                ),
                List.copyOf(
                        deposits
                )
        );
    }

    private SurfaceResourceDeposit collectDeposit(
            String query,
            SurfaceResourcePoint start,
            Map<Long, SurfaceResourcePoint> remaining
    ) {
        ArrayDeque<SurfaceResourcePoint> queue =
                new ArrayDeque<>();

        queue.add(
                start
        );

        int blockCount =
                0;

        int minX =
                Integer.MAX_VALUE;

        int maxX =
                Integer.MIN_VALUE;

        int minZ =
                Integer.MAX_VALUE;

        int maxZ =
                Integer.MIN_VALUE;

        int minY =
                Integer.MAX_VALUE;

        int maxY =
                Integer.MIN_VALUE;

        long totalX =
                0;

        long totalZ =
                0;

        Set<String> codes =
                new TreeSet<>();

        while (!queue.isEmpty()) {
            SurfaceResourcePoint point =
                    queue.removeFirst();

            blockCount++;

            minX =
                    Math.min(
                            minX,
                            point.worldX()
                    );

            maxX =
                    Math.max(
                            maxX,
                            point.worldX()
                    );

            minZ =
                    Math.min(
                            minZ,
                            point.worldZ()
                    );

            maxZ =
                    Math.max(
                            maxZ,
                            point.worldZ()
                    );

            minY =
                    Math.min(
                            minY,
                            point.y()
                    );

            maxY =
                    Math.max(
                            maxY,
                            point.y()
                    );

            totalX +=
                    point.worldX();

            totalZ +=
                    point.worldZ();

            codes.add(
                    point.blockCode()
            );

            for (int dz = -1;
                 dz <= 1;
                 dz++) {

                for (int dx = -1;
                     dx <= 1;
                     dx++) {

                    if (dx == 0
                            && dz == 0) {

                        continue;
                    }

                    int neighborX =
                            point.worldX()
                                    + dx;

                    int neighborZ =
                            point.worldZ()
                                    + dz;

                    SurfaceResourcePoint neighbor =
                            remaining.remove(
                                    coordinateKey(
                                            neighborX,
                                            neighborZ
                                    )
                            );

                    if (neighbor != null) {
                        queue.addLast(
                                neighbor
                        );
                    }
                }
            }
        }

        return new SurfaceResourceDeposit(
                query,
                blockCount,
                codes,
                minX,
                maxX,
                minZ,
                maxZ,
                minY,
                maxY,
                totalX
                        / (double) blockCount,
                totalZ
                        / (double) blockCount
        );
    }

    private long coordinateKey(
            int x,
            int z
    ) {
        return ((long) x << 32)
                ^ (z & 0xFFFFFFFFL);
    }

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
