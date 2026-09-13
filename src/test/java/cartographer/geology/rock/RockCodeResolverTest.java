package cartographer.geology.rock;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RockCodeResolverTest {

    private final RockCodeResolver resolver = new RockCodeResolver();

    @Test
    void resolvesNamespacedAndUnnamespacedNaturalRockCodes() {
        RockIdentity granite = resolver.resolve(
                new BlockInfo(7, "game:rock-granite")
        ).orElseThrow();
        RockIdentity limestone = resolver.resolve(
                new BlockInfo(8, "game:rock-limestone")
        ).orElseThrow();
        RockIdentity shale = resolver.resolve(
                new BlockInfo(9, "rock-shale")
        ).orElseThrow();
        RockIdentity gneiss = resolver.resolve(
                new BlockInfo(10, "somemod:rock-gneiss")
        ).orElseThrow();

        assertEquals(7, granite.blockId());
        assertEquals("game", granite.namespace());
        assertEquals("granite", granite.rockName());
        assertEquals("limestone", limestone.rockName());
        assertEquals("rock-shale", shale.code());
        assertEquals("somemod", gneiss.namespace());
        assertEquals("gneiss", gneiss.rockName());
    }

    @Test
    void rejectsNonNaturalRockAndMalformedCodes() {
        List<String> rejected = List.of(
                "game:ore-cassiterite-granite",
                "game:gravel-granite",
                "game:cobble-granite",
                "game:stonebrick-granite",
                "game:looseboulder-granite",
                "game:rockpolished-granite",
                "rock",
                "game:rock-",
                "game::rock-granite",
                "game:rock granite",
                "contains-rock-name",
                ""
        );

        for (String code : rejected) {
            assertTrue(
                    resolver.resolve(new BlockInfo(1, code)).isEmpty(),
                    code
            );
        }
        assertTrue(resolver.resolve(null).isEmpty());
        assertTrue(resolver.resolve(new BlockInfo(1, null)).isEmpty());
    }

    @Test
    void catalogIgnoresUnrelatedEntriesAndOrdersRecognizedRocks() {
        RockCatalog catalog = RockCatalog.from(Map.of(
                30, new BlockInfo(30, "somemod:rock-gneiss"),
                10, new BlockInfo(10, "game:rock-granite"),
                20, new BlockInfo(20, "game:ore-cassiterite-granite")
        ));

        assertEquals(List.of(10, 30), catalog.rockBlockIds());
        assertEquals(
                List.of("granite", "gneiss"),
                catalog.rocks().stream().map(RockIdentity::rockName).toList()
        );
        assertEquals("game:rock-granite", catalog.findByBlockId(10).orElseThrow().code());
        assertTrue(catalog.findByBlockId(20).isEmpty());
    }
}
