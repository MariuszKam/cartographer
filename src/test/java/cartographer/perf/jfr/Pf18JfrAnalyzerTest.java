package cartographer.perf.jfr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18JfrAnalyzerTest {
    @Test
    void lateHeavyKeyCanDisplaceAnEarlyColdKey() {
        Pf18JfrEventAccumulator.SpaceSaving values =
                new Pf18JfrEventAccumulator.SpaceSaving(Pf18JfrEventAccumulator.CAPACITY);
        for (int index = 0; index < 10; index++) {
            values.offer("cold-" + index, 1);
        }

        values.offer("late-hot", 2);

        assertEquals(10, values.values().size());
        assertTrue(values.values().containsKey("late-hot"));
        assertTrue(!values.values().containsKey("cold-0"));
    }
}
