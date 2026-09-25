package cartographer.index;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceBlockCatalogTest {

    @Test
    void derivesOnlyRealOreBlocksFromRegistryCodes() {
        ResourceBlockCatalog catalog = ResourceBlockCatalog.from(Map.of(
                0, new BlockInfo(0, "game:air"),
                1, new BlockInfo(1, "game:rock-granite"),
                2, new BlockInfo(2, "game:ore-nativecopper-granite"),
                3, new BlockInfo(3, "mod:ore-deep-silver-basalt"),
                4, new BlockInfo(4, "game:decorative-ore-nativecopper")
        ));

        assertArrayEquals(new int[]{2, 3}, catalog.blockIds());
        assertEquals(2, catalog.blocks().size());
        assertTrue(catalog.contains(2));
        assertTrue(catalog.contains(3));
        assertFalse(catalog.contains(1));
        assertFalse(catalog.isEmpty());
    }
}
