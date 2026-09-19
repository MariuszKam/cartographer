package cartographer.save;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackedPositionRunPlannerTest {
    private final PackedPositionRunPlanner planner =
            new PackedPositionRunPlanner();

    @Test
    void compressesOnlyActuallyConsecutivePackedValues() {
        List<PackedPositionRun> runs = planner.plan(
                List.of(10L, 11L, 12L, 20L, 22L, 21L, 40L)
        );

        assertEquals(
                List.of(
                        new PackedPositionRun(10L, 12L, 3),
                        new PackedPositionRun(20L, 22L, 3),
                        new PackedPositionRun(40L, 40L, 1)
                ),
                runs
        );
    }

    @Test
    void duplicatesDoNotInflateRunLength() {
        assertEquals(
                List.of(new PackedPositionRun(5L, 7L, 3)),
                planner.plan(List.of(5L, 6L, 6L, 7L, 5L))
        );
    }

    @Test
    void requiresClearStatementReductionBeforeChoosingRangeStrategy() {
        assertTrue(planner.rangeStrategyClearlyBetter(
                4096,
                List.of(
                        new PackedPositionRun(0, 2047, 2048),
                        new PackedPositionRun(4096, 6143, 2048)
                ),
                256,
                64
        ));

        List<PackedPositionRun> sparse = java.util.stream.LongStream
                .range(0, 257)
                .mapToObj(value ->
                        new PackedPositionRun(value * 2, value * 2, 1)
                )
                .toList();
        assertFalse(planner.rangeStrategyClearlyBetter(
                257,
                sparse,
                256,
                64
        ));
    }
}
