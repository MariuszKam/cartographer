package cartographer.perf.macro;

import cartographer.perf.metrics.PerformanceEnvironment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroBaselineEnvironmentTest {
    @Test
    void capturesValidRuntimeEnvironmentEvidence() {
        PerformanceEnvironment environment = MacroBaselineEnvironment.capture();

        assertFalse(environment.javaVersion().isBlank());
        assertFalse(environment.jvmVendor().isBlank());
        assertFalse(environment.osName().isBlank());
        assertFalse(environment.osVersion().isBlank());
        assertFalse(environment.osArchitecture().isBlank());
        assertTrue(environment.availableProcessors() > 0);
        assertTrue(environment.configuredMaxHeapBytes() > 0);
    }
}
