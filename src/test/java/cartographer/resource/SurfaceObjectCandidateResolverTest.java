package cartographer.resource;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurfaceObjectCandidateResolverTest {
    private final SurfaceObjectCandidateResolver resolver =
            new SurfaceObjectCandidateResolver();

    @Test
    void qualifiedKeysSelectOnlyTheirNamespace() {
        SurfaceObjectCandidateCatalog catalog = catalog();

        assertEquals("game:obsidian", resolver.resolve(catalog, "game:obsidian")
                .qualifiedResourceKey());
        assertEquals("somemod:obsidian", resolver.resolve(catalog, "somemod:obsidian")
                .qualifiedResourceKey());
    }

    @Test
    void unqualifiedKeyIsAllowedOnlyWhenUnique() {
        SurfaceObjectCandidateCatalog catalog = catalog();
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(catalog, "obsidian"));

        SurfaceObjectCandidateCatalog unique = new SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(3, new BlockInfo(3, "game:looseores-nativecopper-granite-free")));
        assertEquals("game:nativecopper", resolver.resolve(unique, "nativecopper")
                .qualifiedResourceKey());
    }

    private SurfaceObjectCandidateCatalog catalog() {
        return new SurfaceObjectCandidateCatalogBuilder().build(Map.of(
                1, new BlockInfo(1, "game:loosestones-obsidian-free"),
                2, new BlockInfo(2, "somemod:loosestones-obsidian-free")
        ));
    }
}
