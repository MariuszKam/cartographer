package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceBlock;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainHeightSurfaceScannerTest {

    private static final RainHeightSurfaceTarget TARGET =
            new RainHeightSurfaceTarget(1, 45, 1);

    @Test
    void extractsExactTargetBlock() {
        ParsedChunk chunk = chunkWith(TARGET, 7, 8);

        RainHeightSurfaceScanResult result = scan(chunk, false, false);

        assertEquals(1, result.blocks().size());
        assertEquals(7, result.blocks().getFirst().blockInfo().id());
        assertTrue(result.unresolvedTargets().isEmpty());
    }

    @Test
    void doesNotScanUnrelatedVoxel() {
        ParsedChunk chunk = chunkWith(TARGET, 7, 8);
        int[] blocks = chunk.blockIds();
        blocks[0] = 9;
        chunk = new ParsedChunk(
                chunk.coordinate(), chunk.minY(), chunk.sizeX(), chunk.sizeY(),
                chunk.sizeZ(), blocks, chunk.liquidIds(), 0, true, ""
        );

        RainHeightSurfaceScanResult result = scan(chunk, false, false);

        assertEquals(List.of(7), result.blocks().stream()
                .map(block -> block.blockInfo().id()).toList());
    }

    @Test
    void missingChunkMakesTargetUnresolved() {
        RainHeightSurfaceScanResult result = scan(null, false, false);

        assertEquals(List.of(TARGET), result.unresolvedTargets());
    }

    @Test
    void airTargetIsUnresolved() {
        RainHeightSurfaceScanResult result = scan(chunkWith(TARGET, 0, 0), false, false);

        assertTrue(result.blocks().isEmpty());
        assertEquals(List.of(TARGET), result.unresolvedTargets());
    }

    @Test
    void waterLiquidAtTargetProducesWaterSurface() {
        RainHeightSurfaceScanResult result = scan(chunkWith(TARGET, 0, 2), false, true);

        assertEquals(SurfaceClass.WATER, result.blocks().getFirst().surfaceClass());
    }

    @Test
    void ignoredFoliageTargetIsUnresolved() {
        RainHeightSurfaceScanResult result = scan(chunkWith(TARGET, 3, 0), true, false);

        assertTrue(result.blocks().isEmpty());
        assertEquals(List.of(TARGET), result.unresolvedTargets());
    }

    @Test
    void foliageCanBeReturnedWhenIgnoreFoliageFalse() {
        RainHeightSurfaceScanResult result = scan(chunkWith(TARGET, 3, 0), false, false);

        assertEquals(1, result.blocks().size());
    }

    @Test
    void missingLiquidLayerIsUnresolvedWhenRequired() {
        RainHeightSurfaceScanResult result = scan(
                chunkWithUnavailableLiquid(), false, true
        );

        assertTrue(result.blocks().isEmpty());
        assertEquals(List.of(TARGET), result.unresolvedTargets());
    }

    @Test
    void missingLiquidLayerCanReturnSolidBlockWhenNotRequired() {
        RainHeightSurfaceScanResult result = scan(
                chunkWithUnavailableLiquid(), false, false
        );

        assertEquals(1, result.blocks().size());
    }

    @Test
    void targetOutsideParsedChunkGeometryIsUnresolved() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(31, 45, 1);
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(target),
                List.of(new cartographer.model.ChunkPosition(0, 1, 0, 0)),
                List.of()
        );
        RainHeightSurfaceScanner.StreamingSession session = new RainHeightSurfaceScanner()
                .begin(plan, registry(), false, false);
        int[] blocks = new int[2 * 32 * 2];
        int[] liquids = new int[blocks.length];
        session.accept(new ParsedChunk(
                new ChunkCoordinate(0, 1, 0),
                32,
                2,
                32,
                2,
                blocks,
                liquids,
                0,
                true,
                ""
        ));

        RainHeightSurfaceScanResult result = session.finish();
        assertTrue(result.blocks().isEmpty());
        assertEquals(List.of(target), result.unresolvedTargets());
    }

    @Test
    void sameTargetIsNeverBothResolvedAndUnresolved() {
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(TARGET), List.of(TARGET.chunkPosition()), List.of()
        );
        RainHeightSurfaceScanner.StreamingSession session = new RainHeightSurfaceScanner()
                .begin(plan, registry(), false, false);
        session.accept(chunkWith(TARGET, 7, 0));
        RainHeightSurfaceScanResult result = session.finish();

        assertEquals(1, result.blocks().size());
        assertTrue(result.unresolvedTargets().isEmpty());
    }

    @Test
    void finishOrderingIsDeterministic() {
        RainHeightSurfaceTarget first = new RainHeightSurfaceTarget(2, 45, 1);
        RainHeightSurfaceTarget second = new RainHeightSurfaceTarget(1, 45, 34);
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(first, second),
                List.of(first.chunkPosition(), second.chunkPosition()),
                List.of()
        );
        RainHeightSurfaceScanner.StreamingSession session = new RainHeightSurfaceScanner()
                .begin(plan, registry(), false, false);
        session.accept(chunkWith(second, 8, 0));
        session.accept(chunkWith(first, 7, 0));

        List<SurfaceBlock> blocks = session.finish().blocks();
        assertEquals(List.of(1, 34), blocks.stream().map(SurfaceBlock::worldZ).toList());
    }

    @Test
    void duplicateDeliveredChunkDoesNotDuplicateResolvedBlock() {
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(TARGET), List.of(TARGET.chunkPosition()), List.of()
        );
        RainHeightSurfaceScanner.StreamingSession session = new RainHeightSurfaceScanner()
                .begin(plan, registry(), false, false);
        ParsedChunk chunk = chunkWith(TARGET, 7, 0);

        session.accept(chunk);
        session.accept(chunk);

        RainHeightSurfaceScanResult result = session.finish();
        assertEquals(1, result.blocks().size());
        assertTrue(result.unresolvedTargets().isEmpty());
    }

    private RainHeightSurfaceScanResult scan(
            ParsedChunk chunk,
            boolean ignoreFoliage,
            boolean requireLiquid
    ) {
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(TARGET), List.of(TARGET.chunkPosition()), List.of()
        );
        RainHeightSurfaceScanner.StreamingSession session = new RainHeightSurfaceScanner()
                .begin(plan, registry(), ignoreFoliage, requireLiquid);
        if (chunk != null) {
            session.accept(chunk);
        }
        return session.finish();
    }

    private ParsedChunk chunkWith(
            RainHeightSurfaceTarget target,
            int targetBlock,
            int targetLiquid
    ) {
        int sizeX = 32;
        int sizeY = 32;
        int sizeZ = 32;
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        int[] liquids = new int[blocks.length];
        int localX = Math.floorMod(target.worldX(), ChunkCoordinate.SIZE_BLOCKS);
        int localY = target.worldY() - 32;
        int localZ = Math.floorMod(target.worldZ(), ChunkCoordinate.SIZE_BLOCKS);
        blocks[(localY * sizeZ + localZ) * sizeX + localX] = targetBlock;
        liquids[(localY * sizeZ + localZ) * sizeX + localX] = targetLiquid;
        return new ParsedChunk(
                new ChunkCoordinate(
                        Math.floorDiv(target.worldX(), ChunkCoordinate.SIZE_BLOCKS),
                        Math.floorDiv(target.worldY(), ChunkCoordinate.SIZE_BLOCKS),
                        Math.floorDiv(target.worldZ(), ChunkCoordinate.SIZE_BLOCKS)
                ),
                Math.floorDiv(target.worldY(), ChunkCoordinate.SIZE_BLOCKS)
                        * ChunkCoordinate.SIZE_BLOCKS,
                sizeX, sizeY, sizeZ,
                blocks, liquids, 0, true, ""
        );
    }

    private ParsedChunk chunkWithUnavailableLiquid() {
        ParsedChunk chunk = chunkWith(TARGET, 7, 0);
        return new ParsedChunk(
                chunk.coordinate(), chunk.minY(), chunk.sizeX(), chunk.sizeY(),
                chunk.sizeZ(), chunk.blockIds(), chunk.liquidIds(), 0, false,
                "liquid layer not decoded"
        );
    }

    private Map<Integer, BlockInfo> registry() {
        Map<Integer, BlockInfo> registry = new HashMap<>();
        registry.put(0, new BlockInfo(0, "air"));
        registry.put(2, new BlockInfo(2, "water-still"));
        registry.put(3, new BlockInfo(3, "flower-blue"));
        registry.put(7, new BlockInfo(7, "rock-granite"));
        registry.put(8, new BlockInfo(8, "rock-basalt"));
        registry.put(9, new BlockInfo(9, "rock-sandstone"));
        return registry;
    }
}
