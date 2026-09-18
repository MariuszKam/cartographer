package cartographer.perf.jfr;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf18JfrAnalyzerTest {
    @Test
    void lateHeavyKeyCanDisplaceAnEarlyColdKey() {
        Map<String, Long> values = new HashMap<>();
        for (int index = 0; index < 10; index++) {
            Pf18JfrAnalyzer.offerHeavyHitter(values, "cold-" + index, 1);
        }

        Pf18JfrAnalyzer.offerHeavyHitter(values, "late-hot", 2);

        assertEquals(10, values.size());
        assertTrue(values.containsKey("late-hot"));
        assertTrue(!values.containsKey("cold-0"));
    }
}
