package cartographer.perf.macro;

import cartographer.perf.workload.RadiusProfile;
import cartographer.perf.workload.RockUpperWorkload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MacroWorkloadResolverTest {
    @Test
    void resolvesOnlySupportedRockUpperRadii() {
        assertEquals(new RockUpperWorkload(RadiusProfile.R256),
                MacroWorkloadResolver.resolve("ROCK_UPPER_R256"));
        assertEquals(new RockUpperWorkload(RadiusProfile.R512),
                MacroWorkloadResolver.resolve("ROCK_UPPER_R512"));
    }

    @Test
    void rejectsUnsupportedWorkloads() {
        assertThrows(IllegalArgumentException.class,
                () -> MacroWorkloadResolver.resolve("MAP_R128"));
    }
}
