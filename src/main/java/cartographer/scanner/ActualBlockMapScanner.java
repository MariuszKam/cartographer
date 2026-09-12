package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ActualBlockMapScanner {

    public ActualBlockMap scan(
            List<ParsedChunk> chunks,
            Map<Integer, BlockInfo> blockRegistry,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            String match
    ) {
        Objects.requireNonNull(
                chunks,
                "chunks are required"
        );

        Objects.requireNonNull(
                blockRegistry,
                "blockRegistry is required"
        );

        if (radius <= 0) {
            throw new IllegalArgumentException(
                    "radius must be positive"
            );
        }

        if (match == null
                || match.isBlank()) {
            throw new IllegalArgumentException(
                    "match must not be blank"
            );
        }

        String normalizedMatch =
                match.toLowerCase(
                        Locale.ROOT
                );

        long radiusSquared =
                (long) radius
                        * radius;

        Map<Long, MutableCell> cells =
                new HashMap<>();

        long matchingBlocks =
                0L;

        int minMatchedY =
                Integer.MAX_VALUE;

        int maxMatchedY =
                Integer.MIN_VALUE;

        for (ParsedChunk chunk : chunks) {
            Objects.requireNonNull(
                    chunk,
                    "chunks must not contain null"
            );

            int chunkBaseX =
                    chunk.coordinate().x()
                            * ChunkCoordinate.SIZE_BLOCKS;

            int chunkBaseZ =
                    chunk.coordinate().z()
                            * ChunkCoordinate.SIZE_BLOCKS;

            for (int localY = 0;
                 localY < chunk.sizeY();
                 localY++) {

                int worldY =
                        chunk.minY()
                                + localY;

                for (int localZ = 0;
                     localZ < chunk.sizeZ();
                     localZ++) {

                    int worldZ =
                            chunkBaseZ
                                    + localZ;

                    long dz =
                            (long) worldZ
                                    - centerWorldZ;

                    long dzSquared =
                            dz
                                    * dz;

                    if (dzSquared > radiusSquared) {
                        continue;
                    }

                    for (int localX = 0;
                         localX < chunk.sizeX();
                         localX++) {

                        int worldX =
                                chunkBaseX
                                        + localX;

                        long dx =
                                (long) worldX
                                        - centerWorldX;

                        long distanceSquared =
                                dx
                                        * dx
                                        + dzSquared;

                        if (distanceSquared > radiusSquared) {
                            continue;
                        }

                        int blockId =
                                chunk.blockIdAt(
                                        localX,
                                        localY,
                                        localZ
                                );

                        BlockInfo blockInfo =
                                blockRegistry.getOrDefault(
                                        blockId,
                                        BlockInfo.unknown(
                                                blockId
                                        )
                                );

                        String code =
                                normalize(
                                        blockInfo.code()
                                );

                        if (!code.contains(
                                normalizedMatch
                        )) {
                            continue;
                        }

                        matchingBlocks++;

                        minMatchedY =
                                Math.min(
                                        minMatchedY,
                                        worldY
                                );

                        maxMatchedY =
                                Math.max(
                                        maxMatchedY,
                                        worldY
                                );

                        long key =
                                key(
                                        worldX,
                                        worldZ
                                );

                        MutableCell cell =
                                cells.computeIfAbsent(
                                        key,
                                        ignored ->
                                                new MutableCell(
                                                        worldX,
                                                        worldZ
                                                )
                                );

                        cell.add(
                                worldY
                        );
                    }
                }
            }
        }

        List<ActualBlockMapCell> resultCells =
                new ArrayList<>(
                        cells.size()
                );

        for (MutableCell cell : cells.values()) {
            resultCells.add(
                    cell.toImmutable()
            );
        }

        resultCells.sort(
                Comparator.comparingInt(
                                ActualBlockMapCell::worldZ
                        )
                        .thenComparingInt(
                                ActualBlockMapCell::worldX
                        )
        );

        if (resultCells.isEmpty()) {
            minMatchedY =
                    -1;

            maxMatchedY =
                    -1;
        }

        return new ActualBlockMap(
                match,
                centerWorldX,
                centerWorldZ,
                radius,
                matchingBlocks,
                minMatchedY,
                maxMatchedY,
                resultCells
        );
    }

    private String normalize(
            String code
    ) {
        return code == null
                ? ""
                : code.toLowerCase(
                        Locale.ROOT
                );
    }

    private long key(
            int worldX,
            int worldZ
    ) {
        return (((long) worldX) << 32)
                ^ (worldZ & 0xffffffffL);
    }

    private static final class MutableCell {

        private final int worldX;
        private final int worldZ;
        private int matchCount;
        private int minY =
                Integer.MAX_VALUE;
        private int maxY =
                Integer.MIN_VALUE;

        private MutableCell(
                int worldX,
                int worldZ
        ) {
            this.worldX =
                    worldX;

            this.worldZ =
                    worldZ;
        }

        private void add(
                int worldY
        ) {
            matchCount++;

            minY =
                    Math.min(
                            minY,
                            worldY
                    );

            maxY =
                    Math.max(
                            maxY,
                            worldY
                    );
        }

        private ActualBlockMapCell toImmutable() {
            return new ActualBlockMapCell(
                    worldX,
                    worldZ,
                    matchCount,
                    minY,
                    maxY
            );
        }
    }
}
