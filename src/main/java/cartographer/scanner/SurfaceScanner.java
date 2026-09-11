package cartographer.scanner;

import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SurfaceScanner {
    private final SurfaceClassifier classifier =
            new SurfaceClassifier();

    public SurfaceScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, boolean ignoreFoliage) {
        return scan(chunks, registry, ignoreFoliage, ProgressReporter.NONE);
    }

    public SurfaceScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, boolean ignoreFoliage, ProgressReporter progress) {
        Map<Long, SurfaceBlock> surfaceByColumn =
                new HashMap<>();

        int columns = 0;
        int totalColumns = chunks.stream()
                .mapToInt(chunk -> chunk.sizeX() * chunk.sizeZ())
                .sum();

        progress.start("Scanning surface columns");
        for (ParsedChunk chunk : chunks) {
            for (int z = 0; z < chunk.sizeZ(); z++) {
                for (int x = 0; x < chunk.sizeX(); x++) {
                    columns++;
                    progress.progress("Scanning surface columns", columns, totalColumns);
                    SurfaceBlock block = findSurfaceBlock(chunk, registry, x, z, ignoreFoliage);
                    if (block != null) {
                        surfaceByColumn.merge(
                                key(
                                        block.worldX(),
                                        block.worldZ()
                                ),
                                block,
                                (oldValue, newValue) ->
                                        newValue.y() > oldValue.y()
                                                ? newValue
                                                : oldValue
                        );
                    }
                }
            }
        }

        int emptyColumns =
                Math.max(
                        0,
                        columns - surfaceByColumn.size()
                );

        return new SurfaceScanResult(List.copyOf(new ArrayList<>(surfaceByColumn.values())), chunks.size(), columns, emptyColumns);
    }

    private SurfaceBlock findSurfaceBlock(ParsedChunk chunk, Map<Integer, BlockInfo> registry, int localX, int localZ, boolean ignoreFoliage) {
        for (int localY = chunk.sizeY() - 1; localY >= 0; localY--) {
            int blockId = chunk.blockIdAt(localX, localY, localZ);
            int liquidId = chunk.liquidIdAt(localX, localY, localZ);
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

    private long key(
            int worldX,
            int worldZ
    ) {
        return ((long) worldX << 32)
                ^ Integer.toUnsignedLong(
                worldZ
        );
    }
}
