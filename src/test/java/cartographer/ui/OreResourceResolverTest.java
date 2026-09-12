package cartographer.ui;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreResourceResolverTest {

    private final OreResourceResolver resolver = new OreResourceResolver();

    @Test
    void verifiesCassiteriteAgainstOreBlockCodes() {
        OreResource resource = resolver.resolve(
                "game:cassiterite",
                List.of(
                        block(1, "ore-poor-cassiterite-granite"),
                        block(2, "ore-medium-cassiterite-granite"),
                        block(3, "ore-rich-cassiterite-granite")
                )
        );

        assertEquals("cassiterite", resource.match());
        assertTrue(resource.registryVerified());
        assertEquals(3, resource.registryMatchCount());
    }

    @Test
    void resolvesCopperAliasToNativeCopper() {
        OreResource resource = resolver.resolve(
                "game:copper",
                List.of(
                        block(1, "ore-poor-nativecopper-granite"),
                        block(2, "ore-medium-nativecopper-granite")
                )
        );

        assertEquals("nativecopper", resource.match());
        assertTrue(resource.registryVerified());
        assertEquals(2, resource.registryMatchCount());
    }

    @Test
    void verifiesUnknownNamespacedResourceConservatively() {
        OreResource resource = resolver.resolve(
                "mod:deep-silver",
                List.of(
                        block(1, "mod:ore-poor-deep-silver-granite")
                )
        );

        assertEquals("deep-silver", resource.match());
        assertTrue(resource.registryVerified());
        assertEquals(1, resource.registryMatchCount());
    }

    @Test
    void keepsUnresolvedResourceWithOriginalMatch() {
        OreResource resource = resolver.resolve(
                "mod:mysteryium",
                List.of(
                        block(1, "rock-mysteryium-granite")
                )
        );

        assertEquals("mysteryium", resource.match());
        assertFalse(resource.registryVerified());
        assertEquals(0, resource.registryMatchCount());
    }

    @Test
    void doesNotVerifyUnrelatedNonOreBlock() {
        OreResource resource = resolver.resolve(
                "game:cassiterite",
                List.of(
                        block(1, "rock-cassiterite-granite")
                )
        );

        assertFalse(resource.registryVerified());
        assertEquals(0, resource.registryMatchCount());
    }

    @Test
    void resolvesAndSortsDistinctResources() {
        List<OreResource> resources = resolver.resolve(
                List.of(
                        "game:cassiterite",
                        "mod:nativecopper",
                        "game:copper",
                        "mod:deep-silver"
                ),
                Map.of(
                        1, block(1, "ore-cassiterite-granite"),
                        2, block(2, "ore-nativecopper-granite")
                )
        );

        assertEquals(3, resources.size());
        assertEquals("Deep Silver", resources.get(0).displayName());
        assertEquals("Native Copper", resources.get(1).displayName());
        assertEquals("Tin / Cassiterite", resources.get(2).displayName());
        assertEquals("mod:nativecopper", resources.get(1).sourceKey());
    }

    private BlockInfo block(int id, String code) {
        return new BlockInfo(id, code);
    }
}
