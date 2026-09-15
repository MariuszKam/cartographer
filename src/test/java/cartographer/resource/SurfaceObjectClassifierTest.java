package cartographer.resource;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceObjectClassifierTest {
    private final SurfaceObjectClassifier classifier = new SurfaceObjectClassifier();

    @Test
    void classifiesOreBitsAndSeparatesHostRock() {
        SurfaceObjectIdentity identity = classify(
                "game:looseores-nativecopper-granite-free"
        );

        assertEquals(SurfaceObjectFamily.ORE_BITS, identity.family());
        assertEquals("game", identity.namespace());
        assertEquals("nativecopper", identity.resourceKey());
        assertEquals(Optional.of("granite"), identity.hostRock());
        assertEquals(Optional.of("free"), identity.variant());
    }

    @Test
    void normalizesLooseStoneVariantsToOneResource() {
        SurfaceObjectIdentity free = classify("game:loosestones-obsidian-free");
        SurfaceObjectIdentity snow = classify("game:loosestones-obsidian-snow");

        assertEquals(SurfaceObjectFamily.LOOSE_STONE, free.family());
        assertEquals("obsidian", free.resourceKey());
        assertEquals(free.resourceKey(), snow.resourceKey());
    }

    @Test
    void classifiesFlintAndBoulderFamilies() {
        SurfaceObjectIdentity flint = classify("game:looseflints-obsidian-free");
        SurfaceObjectIdentity boulder = classify("game:looseboulders-granite-free");

        assertEquals(SurfaceObjectFamily.FLINT, flint.family());
        assertEquals("obsidian", flint.resourceKey());
        assertEquals(SurfaceObjectFamily.LOOSE_BOULDER, boulder.family());
        assertEquals("granite", boulder.resourceKey());
    }

    @Test
    void acceptsModdedNamespacesAndNormalizesCase() {
        SurfaceObjectIdentity identity = classify(
                "SOMEMOD:LOOSESTONES-SOMETHING-FREE"
        );

        assertEquals("somemod", identity.namespace());
        assertEquals("loosestones-something-free", identity.normalizedPath());
        assertEquals("something", identity.resourceKey());
        assertEquals("SOMEMOD:LOOSESTONES-SOMETHING-FREE", identity.originalCode());
    }

    @Test
    void keepsSameResourceKeyDistinctAcrossNamespaces() {
        SurfaceObjectIdentity vanilla = classify("game:loosestones-something-free");
        SurfaceObjectIdentity modded = classify("somemod:loosestones-something-free");

        assertEquals("something", vanilla.resourceKey());
        assertEquals("something", modded.resourceKey());
        assertEquals("game", vanilla.namespace());
        assertEquals("somemod", modded.namespace());
        assertNotEquals(vanilla.qualifiedResourceKey(), modded.qualifiedResourceKey());
        assertEquals("game:something", vanilla.qualifiedResourceKey());
        assertEquals("somemod:something", modded.qualifiedResourceKey());
    }

    @Test
    void rejectsTerrainAndMalformedCodes() {
        Stream.of(
                "game:rock-obsidian",
                "game:ore-cassiterite-granite",
                "game:soil-medium-normal",
                "game:fireclay",
                "game:mushroom-something",
                "game:loosestones-obsidian-unknown",
                "game:looseores-nativecopper-free"
        ).forEach(code -> assertFalse(classifier.classify(new BlockInfo(1, code)).isPresent(), code));

        assertTrue(classifier.classify((String) null).isEmpty());
        assertTrue(classifier.classify("game:loosestones--free").isEmpty());
    }

    @Test
    void preservesOriginalBlockCodeWhenClassifyingBlockInfo() {
        BlockInfo block = new BlockInfo(42, "game:loosestones-obsidian-ice");

        SurfaceObjectIdentity identity = classifier.classify(block).orElseThrow();

        assertEquals(block.code(), identity.originalCode());
        assertEquals("obsidian", identity.resourceKey());
        assertEquals(Optional.of("ice"), identity.variant());
    }

    private SurfaceObjectIdentity classify(String code) {
        return classifier.classify(code).orElseThrow();
    }
}
