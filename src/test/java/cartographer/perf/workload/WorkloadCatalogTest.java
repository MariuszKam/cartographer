package cartographer.perf.workload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkloadCatalogTest {
    @Test
    void standardRadiiAndIdsAreDeterministic() {
        List<WorkloadSpec> first = WorkloadCatalog.standard(
                ResourceIdentity.of("Native Copper"),
                List.of(ResourceIdentity.of("native copper")),
                96
        );
        List<WorkloadSpec> second = WorkloadCatalog.standard(
                ResourceIdentity.of("native copper"),
                List.of(ResourceIdentity.of("native copper")),
                96
        );

        assertEquals(first, second);
        assertEquals("MAP_R128", first.get(0).id());
        assertEquals("ORE_SINGLE_native_copper_R256", first.get(5).id());
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
                List.of(ResourceIdentity.of("clay")),
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
    void selectedResourceSetDefinesAStableDistinctIdentity() {
        ProspectingWorkload copper = new ProspectingWorkload(
                RadiusProfile.R512,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of(ResourceIdentity.of("copper"))
        );
        ProspectingWorkload tin = new ProspectingWorkload(
                RadiusProfile.R512,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of(ResourceIdentity.of("tin"))
        );
        ProspectingWorkload reordered = new ProspectingWorkload(
                RadiusProfile.R512,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of(ResourceIdentity.of("tin"), ResourceIdentity.of("copper"))
        );
        ProspectingWorkload sameSetDifferentOrder = new ProspectingWorkload(
                RadiusProfile.R512,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of(ResourceIdentity.of("copper"), ResourceIdentity.of("tin"))
        );

        assertEquals(ProspectingStrategy.SELECTED_RESOURCE_SET, copper.strategy());
        assertEquals(List.of(ResourceIdentity.of("copper"), ResourceIdentity.of("tin")),
                reordered.selectedResources());
        assertEquals(reordered, sameSetDifferentOrder);
        assertEquals(reordered.id(), sameSetDifferentOrder.id());
        assertNotEquals(copper.id(), tin.id());
    }

    @Test
    void selectedResourceSetRejectsEmptyNullAndDuplicateEntries() {
        assertThrows(IllegalArgumentException.class, () -> new ProspectingWorkload(
                RadiusProfile.R128,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of()
        ));
        assertThrows(NullPointerException.class, () -> new ProspectingWorkload(
                RadiusProfile.R128,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                null
        ));
        assertThrows(NullPointerException.class, () -> new ProspectingWorkload(
                RadiusProfile.R128,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                java.util.Arrays.asList(ResourceIdentity.of("copper"), null)
        ));
        assertThrows(IllegalArgumentException.class, () -> new ProspectingWorkload(
                RadiusProfile.R128,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                List.of(ResourceIdentity.of("copper"), ResourceIdentity.of("COPPER"))
        ));
    }

    @Test
    void selectedResourceSetDefensivelyCopiesCallerCollection() {
        ArrayList<ResourceIdentity> resources = new ArrayList<>(
                List.of(ResourceIdentity.of("copper"))
        );
        ProspectingWorkload workload = new ProspectingWorkload(
                RadiusProfile.R128,
                ProspectingStrategy.SELECTED_RESOURCE_SET,
                resources
        );
        resources.add(ResourceIdentity.of("tin"));

        assertEquals(List.of(ResourceIdentity.of("copper")), workload.selectedResources());
        assertThrows(UnsupportedOperationException.class, () ->
                workload.selectedResources().add(ResourceIdentity.of("tin")));
    }

    @Test
    void fullProspectingUsesStrategyWithoutSelectedResources() {
        ProspectingWorkload full = new ProspectingWorkload(
                RadiusProfile.R1024,
                ProspectingStrategy.ALL_DISCOVERED_SUPPORTED_RESOURCES,
                List.of()
        );

        assertEquals(WorkloadFamily.PROSPECTING_FULL, full.family());
        assertEquals("PROSPECTING_FULL_R1024", full.id());
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
