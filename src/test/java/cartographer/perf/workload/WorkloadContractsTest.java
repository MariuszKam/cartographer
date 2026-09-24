package cartographer.perf.workload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkloadContractsTest {
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
}
