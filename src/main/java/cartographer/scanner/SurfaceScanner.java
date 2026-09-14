package cartographer.scanner;

import cartographer.application.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SurfaceScanner {
    private final SurfaceClassifier classifier =
            new SurfaceClassifier();

    public SurfaceScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, boolean ignoreFoliage) {
        return scan(chunks, registry, ignoreFoliage, ProgressReporter.NONE);
    }

    public SurfaceScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, boolean ignoreFoliage, ProgressReporter progress) {
        int columns = 0;
        int totalColumns = chunks.stream()
                .mapToInt(chunk -> chunk.sizeX() * chunk.sizeZ())
                .sum();

        List<ParsedChunk> orderedChunks =
                chunks.stream()
                        .sorted(
                                Comparator.comparingInt(
                                                (ParsedChunk chunk) ->
                                                        chunk.coordinate()
                                                                .x()
                                        )
                                        .thenComparingInt(
                                                chunk ->
                                                        chunk.coordinate()
                                                                .z()
                                        )
                        )
                        .toList();

        List<SurfaceBlock> surfaceBlocks =
                new ArrayList<>();

        int consideredColumnCount =
                0;

        int liquidUnavailableColumnCount =
                0;

        progress.start("Scanning surface columns");
        int index =
                0;

        while (index < orderedChunks.size()) {
            ParsedChunk first =
                    orderedChunks.get(
                            index
                    );

            int chunkX =
                    first.coordinate()
                            .x();

            int chunkZ =
                    first.coordinate()
                            .z();

            ChunkColumnState state =
                    new ChunkColumnState();

            while (index < orderedChunks.size()
                    && orderedChunks.get(index)
                    .coordinate()
                    .x() == chunkX
                    && orderedChunks.get(index)
                    .coordinate()
                    .z() == chunkZ) {

                ParsedChunk chunk =
                        orderedChunks.get(
                                index
                        );

                for (int z = 0; z < chunk.sizeZ(); z++) {
                    for (int x = 0; x < chunk.sizeX(); x++) {
                        columns++;
                        progress.progress("Scanning surface columns", columns, totalColumns);
                        state.consider(
                                x,
                                z
                        );

                        if (!chunk.liquidLayerAvailable()) {
                            state.markLiquidUnavailable(
                                    x,
                                    z
                            );
                        }

                        SurfaceBlock block = findSurfaceBlock(chunk, registry, x, z, ignoreFoliage);
                        if (block != null) {
                            state.recordSurface(
                                    x,
                                    z,
                                    block
                            );
                        }
                    }
                }

                index++;
            }

            consideredColumnCount +=
                    state.consideredColumns();

            liquidUnavailableColumnCount +=
                    state.liquidUnavailableColumns();

            state.appendSurfaceBlocksTo(
                    surfaceBlocks
            );
        }

        int emptyColumns =
                Math.max(
                        0,
                        consideredColumnCount
                                - surfaceBlocks.size()
                );

        return new SurfaceScanResult(
                List.copyOf(
                        surfaceBlocks
                ),
                chunks.size(),
                consideredColumnCount,
                emptyColumns,
                liquidUnavailableColumnCount
        );
    }

    private SurfaceBlock findSurfaceBlock(ParsedChunk chunk, Map<Integer, BlockInfo> registry, int localX, int localZ, boolean ignoreFoliage) {
        for (int localY = chunk.sizeY() - 1; localY >= 0; localY--) {
            int blockId = chunk.blockIdAt(localX, localY, localZ);
            int liquidId =
                    chunk.liquidLayerAvailable()
                            ? chunk.liquidIdAt(
                            localX,
                            localY,
                            localZ
                    )
                            : 0;
            BlockInfo blockInfo = registry.getOrDefault(blockId, BlockInfo.unknown(blockId));
            BlockInfo liquidInfo = registry.getOrDefault(liquidId, BlockInfo.unknown(liquidId));
            SurfaceClass surfaceClass =
                    classifier.classify(
                            blockInfo,
                            liquidInfo
                    );

            if (surfaceClass == SurfaceClass.WATER) {
                return new SurfaceBlock(
                        chunk.worldX(localX),
                        chunk.worldY(localY),
                        chunk.worldZ(localZ),
                        blockInfo,
                        liquidId,
                        liquidInfo,
                        surfaceClass
                );
            }

            if (blockInfo.isAir()) {
                continue;
            }
            if (ignoreFoliage && blockInfo.isFoliage()) {
                continue;
            }

            return new SurfaceBlock(
                    chunk.worldX(localX),
                    chunk.worldY(localY),
                    chunk.worldZ(localZ),
                    blockInfo,
                    liquidId,
                    liquidInfo,
                    surfaceClass
            );
        }
        return null;
    }

    private static class ChunkColumnState {
        private static final int COLUMN_SIZE =
                32;

        private static final int COLUMN_COUNT =
                COLUMN_SIZE
                        * COLUMN_SIZE;

        private final SurfaceBlock[] surfaceBlocks =
                new SurfaceBlock[COLUMN_COUNT];

        private final boolean[] consideredColumns =
                new boolean[COLUMN_COUNT];

        private final boolean[] liquidUnavailableColumns =
                new boolean[COLUMN_COUNT];

        private int consideredColumnCount;

        private int liquidUnavailableColumnCount;

        void consider(
                int localX,
                int localZ
        ) {
            int index =
                    index(
                            localX,
                            localZ
                    );

            if (!consideredColumns[index]) {
                consideredColumns[index] =
                        true;

                consideredColumnCount++;
            }
        }

        void markLiquidUnavailable(
                int localX,
                int localZ
        ) {
            int index =
                    index(
                            localX,
                            localZ
                    );

            if (!liquidUnavailableColumns[index]) {
                liquidUnavailableColumns[index] =
                        true;

                liquidUnavailableColumnCount++;
            }
        }

        void recordSurface(
                int localX,
                int localZ,
                SurfaceBlock block
        ) {
            int index =
                    index(
                            localX,
                            localZ
                    );

            SurfaceBlock current =
                    surfaceBlocks[index];

            if (current == null
                    || block.y() > current.y()) {

                surfaceBlocks[index] =
                        block;
            }
        }

        int consideredColumns() {
            return consideredColumnCount;
        }

        int liquidUnavailableColumns() {
            return liquidUnavailableColumnCount;
        }

        void appendSurfaceBlocksTo(
                List<SurfaceBlock> output
        ) {
            for (SurfaceBlock block : surfaceBlocks) {
                if (block != null) {
                    output.add(
                            block
                    );
                }
            }
        }

        private int index(
                int localX,
                int localZ
        ) {
            if (localX < 0
                    || localX >= COLUMN_SIZE
                    || localZ < 0
                    || localZ >= COLUMN_SIZE) {

                throw new IndexOutOfBoundsException(
                        "Local surface column out of bounds"
                );
            }

            return localZ
                    * COLUMN_SIZE
                    + localX;
        }
    }
}
