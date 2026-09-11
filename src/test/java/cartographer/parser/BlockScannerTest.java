package cartographer.parser;

import cartographer.analysis.BlockScanResult;
import cartographer.analysis.BlockScanner;
import cartographer.cli.ProgressReporter;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockScannerTest {
    @Test
    void findsBlocksByCodePatternAndHonorsLimit() {
        ParsedChunk chunk = new ParsedChunk(new ChunkCoordinate(0, 0), 0, 2, 1, 2, new int[]{1, 2, 2, 3});
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:ore-copper"),
                3, new BlockInfo(3, "game:soil-medium"));

        BlockScanResult result = new BlockScanner().scan(List.of(chunk), registry, "copper", 1, ProgressReporter.NONE);

        assertEquals(2, result.blocksScanned());
        assertEquals(1, result.matches().size());
        assertTrue(result.truncated());
        assertEquals("game:ore-copper", result.matches().get(0).blockInfo().code());
    }
}
