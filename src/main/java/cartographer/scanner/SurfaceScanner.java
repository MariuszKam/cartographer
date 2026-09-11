package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SurfaceScanner {
    public SurfaceScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, boolean ignoreFoliage) {
        List<SurfaceBlock> surfaceBlocks = new ArrayList<>();
        int columns = 0;
        int emptyColumns = 0;

        for (ParsedChunk chunk : chunks) {
            for (int z = 0; z < chunk.sizeZ(); z++) {
                for (int x = 0; x < chunk.sizeX(); x++) {
                    columns++;
                    SurfaceBlock block = findSurfaceBlock(chunk, registry, x, z, ignoreFoliage);
                    if (block == null) {
                        emptyColumns++;
                    } else {
                        surfaceBlocks.add(block);
                    }
                }
            }
        }

        return new SurfaceScanResult(List.copyOf(surfaceBlocks), chunks.size(), columns, emptyColumns);
    }

    private SurfaceBlock findSurfaceBlock(ParsedChunk chunk, Map<Integer, BlockInfo> registry, int localX, int localZ, boolean ignoreFoliage) {
        for (int localY = chunk.sizeY() - 1; localY >= 0; localY--) {
            int blockId = chunk.blockIdAt(localX, localY, localZ);
            BlockInfo blockInfo = registry.getOrDefault(blockId, BlockInfo.unknown(blockId));
            if (blockInfo.isAir()) {
                continue;
            }
            if (ignoreFoliage && blockInfo.isFoliage()) {
                continue;
            }
            return new SurfaceBlock(chunk.worldX(localX), chunk.minY() + localY, chunk.worldZ(localZ), blockInfo);
        }
        return null;
    }
}
