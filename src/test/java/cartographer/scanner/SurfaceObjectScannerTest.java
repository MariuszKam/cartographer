package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectScannerTest {
    private static final WorldMetadata WORLD = new WorldMetadata(128, 64, 128);
    private static final BlockInfo OBSIDIAN =
            new BlockInfo(7, "game:loosestones-obsidian-free");
    private static final SurfaceObjectScanner SCANNER = new SurfaceObjectScanner();

    @Test
    void findsObjectAboveRainHeight() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 10, 10);
        ParsedChunk chunk = chunk(target, 11, 7);

        SurfaceObjectScanResult result = scan(target, List.of(chunk), Set.of(position(chunk)));

        assertEquals(1, result.observedObjects());
        assertEquals(11, result.blocks().getFirst().y());
        assertEquals(0, result.unavailablePositions());
    }

    @Test
    void checksTheNextVerticalChunkAtBoundary() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 31, 10);
        ParsedChunk upper = chunk(target, 32, 7);
        Set<cartographer.model.ChunkPosition> positions = Set.of(
                position(chunk(target, 31, 1)), position(upper)
        );

        SurfaceObjectScanResult result = scan(target, List.of(upper), positions);

        assertEquals(1, result.observedObjects());
        assertEquals(32, result.blocks().getFirst().y());
    }

    @Test
    void paletteRejectedAvailableChunksMeanNotObserved() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 10, 10);

        SurfaceObjectScanResult result = scan(
                target,
                List.of(),
                Set.of(
                        new cartographer.model.ChunkPosition(0, 0, 0, 0)
                )
        );

        assertTrue(result.blocks().isEmpty());
        assertEquals(0, result.unavailablePositions());
    }

    @Test
    void missingRequiredChunkIsUnavailable() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 31, 10);
        ParsedChunk lower = chunk(target, 31, 1);

        SurfaceObjectScanResult result = scan(
                target,
                List.of(lower),
                Set.of(position(lower))
        );

        assertTrue(result.blocks().isEmpty());
        assertEquals(1, result.unavailablePositions());
    }

    @Test
    void observedObjectWinsOverMissingCandidateChunk() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 10, 10);
        SurfaceObjectPlan plan = new SurfaceObjectPlan(
                List.of(new SurfaceObjectTarget(10, 10, List.of(10, 11, 32))),
                List.of()
        );
        ParsedChunk chunk = chunk(target, 11, 7);

        SurfaceObjectScanResult result = SCANNER.scan(
                plan,
                registry(),
                Set.of(7),
                List.of(chunk),
                Set.of(position(chunk))
        );

        assertEquals(1, result.observedObjects());
        assertEquals(11, result.blocks().getFirst().y());
        assertEquals(0, result.unavailablePositions());
    }

    @Test
    void rejectsRockAndOreCodesWhenTheyAreNotWantedIds() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(10, 10, 10);
        BlockInfo rock = new BlockInfo(8, "game:rock-obsidian");
        BlockInfo ore = new BlockInfo(9, "game:ore-iron-obsidian");

        assertTrue(scan(target, List.of(chunk(target, 11, rock.id())),
                Set.of(position(chunk(target, 11, rock.id())))).blocks().isEmpty());
        assertTrue(scan(target, List.of(chunk(target, 11, ore.id())),
                Set.of(position(chunk(target, 11, ore.id())))).blocks().isEmpty());
    }

    @Test
    void supportsNegativeWorldCoordinates() {
        RainHeightSurfaceTarget target = new RainHeightSurfaceTarget(-1, 10, -1);
        ParsedChunk chunk = chunk(target, 11, 7);

        SurfaceObjectScanResult result = scan(target, List.of(chunk), Set.of(position(chunk)));

        assertEquals(-1, result.blocks().getFirst().worldX());
        assertEquals(-1, result.blocks().getFirst().worldZ());
    }

    @Test
    void ordersMultipleObjectsDeterministically() {
        RainHeightSurfaceTarget first = new RainHeightSurfaceTarget(2, 10, 1);
        RainHeightSurfaceTarget second = new RainHeightSurfaceTarget(34, 10, 2);
        ParsedChunk firstChunk = chunk(first, 11, 7);
        ParsedChunk secondChunk = chunk(second, 11, 7);
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(second, first),
                List.of(position(firstChunk), position(secondChunk)),
                List.of()
        );

        SurfaceObjectScanResult result = SCANNER.scan(
                plan,
                WORLD,
                registry(),
                Set.of(7),
                List.of(firstChunk, secondChunk),
                Set.of(position(firstChunk), position(secondChunk))
        );

        assertEquals(List.of(2, 34), result.blocks().stream()
                .map(block -> block.worldX()).toList());
    }

    private SurfaceObjectScanResult scan(
            RainHeightSurfaceTarget target,
            List<ParsedChunk> chunks,
            Set<cartographer.model.ChunkPosition> available
    ) {
        RainHeightSurfacePlan plan = new RainHeightSurfacePlan(
                List.of(target), List.of(target.chunkPosition()), List.of()
        );
        return SCANNER.scan(plan, WORLD, registry(), Set.of(7), chunks, available);
    }

    private ParsedChunk chunk(RainHeightSurfaceTarget target, int worldY, int blockId) {
        int chunkY = Math.floorDiv(worldY, ChunkCoordinate.SIZE_BLOCKS);
        ChunkCoordinate coordinate = new ChunkCoordinate(
                Math.floorDiv(target.worldX(), ChunkCoordinate.SIZE_BLOCKS),
                chunkY,
                Math.floorDiv(target.worldZ(), ChunkCoordinate.SIZE_BLOCKS)
        );
        int[] blocks = new int[32 * 32 * 32];
        int localX = Math.floorMod(target.worldX(), 32);
        int localY = worldY - chunkY * 32;
        int localZ = Math.floorMod(target.worldZ(), 32);
        blocks[(localY * 32 + localZ) * 32 + localX] = blockId;
        return new ParsedChunk(coordinate, chunkY * 32, 32, 32, 32, blocks);
    }

    private cartographer.model.ChunkPosition position(ParsedChunk chunk) {
        return new cartographer.model.ChunkPosition(
                chunk.coordinate().x(), chunk.coordinate().y(), chunk.coordinate().z(), 0
        );
    }

    private Map<Integer, BlockInfo> registry() {
        Map<Integer, BlockInfo> registry = new HashMap<>();
        registry.put(1, new BlockInfo(1, "game:soil-grass"));
        registry.put(7, OBSIDIAN);
        return registry;
    }
}
