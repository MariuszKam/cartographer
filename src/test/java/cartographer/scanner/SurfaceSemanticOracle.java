package cartographer.scanner;

import cartographer.model.SurfaceBlock;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/** Test-only semantic normalization; never use this as a production mode. */
final class SurfaceSemanticOracle {
    private SurfaceSemanticOracle() {
    }

    static List<Cell> normalize(List<SurfaceBlock> blocks) {
        return blocks.stream()
                .map(block -> new Cell(
                        block.worldX(),
                        block.worldZ(),
                        block.y(),
                        block.blockInfo().id(),
                        block.liquidBlockId(),
                        block.surfaceClass()
                ))
                .sorted(Comparator.comparingInt(Cell::worldZ)
                        .thenComparingInt(Cell::worldX)
                        .thenComparingInt(Cell::surfaceY)
                        .thenComparingInt(Cell::blockId)
                        .thenComparingInt(Cell::liquidBlockId)
                        .thenComparing(Cell::surfaceClass))
                .toList();
    }

    static String fingerprint(SurfaceMap map) {
        StringJoiner result = new StringJoiner(";");
        map.forEachCell((x, z, state, y, blockId, liquidId, surfaceClass) ->
                result.add(x + "," + z + "," + state + "," + y + ","
                        + blockId + "," + liquidId + "," + surfaceClass));
        return result.toString();
    }

    record Cell(
            int worldX,
            int worldZ,
            int surfaceY,
            int blockId,
            int liquidBlockId,
            cartographer.model.SurfaceClass surfaceClass
    ) {
    }
}
