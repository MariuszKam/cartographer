package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.model.SurfaceClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceSemanticOracleTest {
    @Test
    void normalizesSemanticValuesRatherThanBlockInfoIdentity() {
        List<SurfaceBlock> first = List.of(
                new SurfaceBlock(2, 11, 1,
                        new BlockInfo(7, "game:soil"),
                        8, new BlockInfo(8, "game:water"), SurfaceClass.SOIL)
        );
        List<SurfaceBlock> second = List.of(
                new SurfaceBlock(2, 11, 1,
                        new BlockInfo(7, "other-description"),
                        8, new BlockInfo(8, "other-water-description"), SurfaceClass.SOIL)
        );

        assertEquals(
                SurfaceSemanticOracle.normalize(first),
                SurfaceSemanticOracle.normalize(second)
        );
    }

    @Test
    void normalizationOrderIsDeterministic() {
        SurfaceBlock first = new SurfaceBlock(3, 10, 2,
                new BlockInfo(2, "game:a"));
        SurfaceBlock second = new SurfaceBlock(1, 12, 1,
                new BlockInfo(1, "game:b"));

        assertEquals(
                List.of(1, 2),
                SurfaceSemanticOracle.normalize(List.of(first, second)).stream()
                        .map(SurfaceSemanticOracle.Cell::worldX)
                        .toList()
        );
    }
}
