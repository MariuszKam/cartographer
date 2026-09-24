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
        List<PackedPositionRun> runs = planner.planIfClearlyBetter(
                List.of(10L, 11L, 12L, 20L, 22L, 21L, 40L),
                1,
                64
        ).orElseThrow();

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
                planner.planIfClearlyBetter(
                        List.of(5L, 6L, 6L, 7L, 5L),
                        1,
                        64
                ).orElseThrow()
        );
    }

    @Test
    void requiresClearStatementReductionBeforeChoosingRangeStrategy() {
        assertTrue(planner.planIfClearlyBetter(
                java.util.stream.LongStream.range(0, 4096).boxed().toList(),
                256,
                64
        ).isPresent());

        List<Long> sparse = java.util.stream.LongStream
                .range(0, 257)
                .map(value -> value * 2)
                .boxed()
                .toList();
        assertFalse(planner.planIfClearlyBetter(
                sparse,
                256,
                64
        ).isPresent());
    }
}
