package cartographer.analysis;

import cartographer.application.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockScanner {
    public BlockScanResult scan(List<ParsedChunk> chunks, Map<Integer, BlockInfo> registry, String match, int limit, ProgressReporter progress) {
        String normalizedMatch = match.toLowerCase(Locale.ROOT);
        List<BlockMatch> matches = new ArrayList<>();
        int blocksScanned = 0;
        int totalBlocks = chunks.stream()
                .mapToInt(chunk -> chunk.sizeX() * chunk.sizeY() * chunk.sizeZ())
                .sum();

        progress.start("Scanning blocks");
        for (ParsedChunk chunk : chunks) {
            for (int y = 0; y < chunk.sizeY(); y++) {
                for (int z = 0; z < chunk.sizeZ(); z++) {
                    for (int x = 0; x < chunk.sizeX(); x++) {
                        blocksScanned++;
                        progress.progress("Scanning blocks", blocksScanned, totalBlocks);
                        int blockId = chunk.blockIdAt(x, y, z);
                        BlockInfo block = registry.getOrDefault(blockId, BlockInfo.unknown(blockId));
                        if (block.code().toLowerCase(Locale.ROOT).contains(normalizedMatch)) {
                            matches.add(new BlockMatch(chunk.worldX(x), chunk.minY() + y, chunk.worldZ(z), block));
                            if (matches.size() >= limit) {
                                return new BlockScanResult(chunks.size(), blocksScanned, List.copyOf(matches), true);
                            }
                        }
                    }
                }
            }
        }

        return new BlockScanResult(chunks.size(), blocksScanned, List.copyOf(matches), false);
    }
}
