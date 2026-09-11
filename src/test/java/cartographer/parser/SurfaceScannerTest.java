package cartographer.parser;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.scanner.SurfaceScanResult;
import cartographer.scanner.SurfaceScanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceScannerTest {
    @Test
    void findsTopMostNonAirAndCanIgnoreFoliage() {
        ParsedChunk chunk = new ParsedChunk(
                new ChunkCoordinate(0, 0),
                10,
                1,
                3,
                1,
                new int[]{1, 2, 3});
        Map<Integer, BlockInfo> registry = Map.of(
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:soil-medium"),
                3, new BlockInfo(3, "game:leaves-oak"));

        SurfaceScanResult result = new SurfaceScanner().scan(List.of(chunk), registry, true);

        assertEquals(1, result.blocks().size());
        assertEquals(11, result.blocks().get(0).y());
        assertEquals("game:soil-medium", result.blocks().get(0).blockInfo().code());
    }
}
