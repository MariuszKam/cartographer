package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceMapScanResultTest {
    @Test
    void diagnosticsReadPrimitiveMapWithoutBulkSurfaceBlocks() {
        SurfaceTileLayout layout = SurfaceTileLayout.forSurface(
                16, 16, 4, new WorldMetadata(64, 256, 64));
        SurfaceTileAccumulator accumulator = new SurfaceTileAccumulator(layout);
        accumulator.recordSurface(16, 16, 20, 7, 8, SurfaceClass.WATER);
        accumulator.recordSurface(17, 16, 21, 9, 0, SurfaceClass.UNKNOWN);
        accumulator.recordSurface(18, 16, 22, 404, 0, SurfaceClass.UNKNOWN);
        accumulator.recordSurface(19, 16, 23, 404, 0, SurfaceClass.UNKNOWN);

        SurfaceMapScanResult result = new SurfaceMapScanResult(
                accumulator.finish(),
                Map.of(
                        7, new BlockInfo(7, "game:water"),
                        9, new BlockInfo(9, "mod:unknown-rock")
                ),
                2, 2, 0, 0
        );

        assertEquals(1, result.waterColumns());
        assertEquals(1, result.unknownSurfaceBlocks());
        assertEquals("unknown:404", result.topUnknownSurfaceBlockCodes(1).get(0).code());
        assertEquals(2, result.topUnknownSurfaceBlockCodes(1).get(0).count());
        assertEquals(1, result.map().tileCount());
    }
}
