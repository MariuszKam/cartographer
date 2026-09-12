package cartographer.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceCatalogServiceTest {

    @Test
    void normalizesNamespacesAndFriendlyNames() {
        List<OreResource> resources = ResourceCatalogService.resourcesFromKeys(
                List.of("game:cassiterite", "mod:deep-silver")
        );

        assertEquals("Tin / Cassiterite", resources.get(1).displayName());
        assertEquals("cassiterite", resources.get(1).match());
        assertEquals("game:cassiterite", resources.get(1).sourceKey());
        assertEquals("Deep Silver", resources.get(0).displayName());
        assertEquals("deep-silver", resources.get(0).match());
    }

    @Test
    void sortsByDisplayNameAndCollapsesDuplicateShortNames() {
        List<OreResource> resources = ResourceCatalogService.resourcesFromKeys(
                List.of(
                        "game:cassiterite",
                        "nativecopper",
                        "mod:nativecopper",
                        "mod:deep-silver"
                )
        );

        assertEquals(3, resources.size());
        assertEquals("Deep Silver", resources.get(0).displayName());
        assertEquals("Native Copper", resources.get(1).displayName());
        assertEquals("Tin / Cassiterite", resources.get(2).displayName());
        assertEquals("nativecopper", resources.get(1).match());
        assertEquals("nativecopper", resources.get(1).sourceKey());
    }
}
