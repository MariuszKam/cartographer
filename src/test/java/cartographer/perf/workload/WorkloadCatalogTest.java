package cartographer.perf.workload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkloadCatalogTest {
    @Test
    void standardRadiiAndIdsAreDeterministic() {
        List<WorkloadSpec> first = WorkloadCatalog.standard(
                ResourceIdentity.of("Native Copper"),
                96
        );
        List<WorkloadSpec> second = WorkloadCatalog.standard(
                ResourceIdentity.of("native copper"),
                96
        );

        assertEquals(first, second);
        assertEquals("MAP_R128", first.get(0).id());
        assertEquals("ORE_SINGLE_NATIVE_COPPER_R256", first.get(5).id());
        assertEquals("ROCK_AT_Y_Y96_R1024", first.get(15).id());
        assertEquals("PROSPECTING_FULL_R512", first.get(26).id());
        assertEquals(List.of(128, 256, 512, 1024),
                first.stream().limit(4).map(workload -> workload.radius().blocks()).toList());
    }

    @Test
    void resourceIdentityIsLocaleIndependentAndRejectsBlankValues() {
        assertEquals("native copper", ResourceIdentity.of("  NATIVE   COPPER  ").value());
        assertThrows(IllegalArgumentException.class, () -> ResourceIdentity.of("  "));
        assertThrows(NullPointerException.class, () -> ResourceIdentity.of(null));
        assertThrows(NullPointerException.class, () ->
                new OreSingleWorkload(RadiusProfile.R128, null));
    }

    @Test
    void rockAtYRequiresAnExplicitTypedCoordinate() {
        RockAtYWorkload workload = new RockAtYWorkload(RadiusProfile.R512, -32);

        assertEquals(-32, workload.absoluteWorldY());
        assertEquals("ROCK_AT_Y_Y-32_R512", workload.id());
    }

    @Test
    void catalogIsImmutableAndHasStableOrdering() {
        List<WorkloadSpec> catalog = WorkloadCatalog.standard(
                ResourceIdentity.of("clay"),
                80
        );

        assertEquals(28, catalog.size());
        assertThrows(UnsupportedOperationException.class, () ->
                catalog.add(new MapWorkload(RadiusProfile.R128)));
        List<WorkloadFamily> expectedFamilies = List.of(
                WorkloadFamily.MAP,
                WorkloadFamily.ORE_SINGLE,
                WorkloadFamily.SURFACE_SINGLE,
                WorkloadFamily.ROCK_AT_Y,
                WorkloadFamily.ROCK_UPPER,
                WorkloadFamily.PROSPECTING_SMALL,
                WorkloadFamily.PROSPECTING_FULL
        );
        assertEquals(expectedFamilies,
                catalog.stream()
                        .map(WorkloadSpec::family)
                        .distinct()
                        .toList());
    }

    @Test
    void geometryMatchesExpectedExactLatticeCounts() {
        assertEquals(51_433L, WorkloadGeometry.exactLatticeColumnCount(RadiusProfile.R128));
        assertEquals(205_861L, WorkloadGeometry.exactLatticeColumnCount(RadiusProfile.R256));
        assertEquals(823_473L, WorkloadGeometry.exactLatticeColumnCount(RadiusProfile.R512));
        assertEquals(3_294_097L, WorkloadGeometry.exactLatticeColumnCount(RadiusProfile.R1024));
        assertEquals(
                Math.PI * 512 * 512,
                WorkloadGeometry.approximateCircularArea(RadiusProfile.R512)
        );
    }
}
