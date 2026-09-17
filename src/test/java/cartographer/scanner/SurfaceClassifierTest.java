package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceClassifierTest {

    private final SurfaceClassifier classifier =
            new SurfaceClassifier();

    @Test
    void classifiesForestFloorSeparately() {
        BlockInfo block =
                new BlockInfo(
                        1,
                        "forestfloor-5"
                );

        assertFalse(
                block.isFoliage()
        );

        assertEquals(
                SurfaceClass.FOREST_FLOOR,
                classifier.classify(
                        block,
                        BlockInfo.unknown(0)
                )
        );
    }

    @Test
    void recognizesObservedVegetationFamilies() {
        List<String> codes =
                List.of(
                        "fern-eaglefern",
                        "wildvine-section-north",
                        "fruitingbush-wild-whitecurrant-free",
                        "tallplant-coopersreed-land-normal-free",
                        "log-grown-oak-ud",
                        "log-grown-maple-ud",
                        "log-grown-pine-ud",
                        "leaves-grown3-birch",
                        "leavesbranchy-grown3-birch"
                );

        for (String code : codes) {
            BlockInfo block =
                    new BlockInfo(
                            10,
                            code
                    );

            assertTrue(
                    block.isFoliage(),
                    code
            );

            assertEquals(
                    SurfaceClass.VEGETATION,
                    classifier.classify(
                            block,
                            BlockInfo.unknown(0)
                    ),
                    code
            );
        }
    }

    @Test
    void sandstoneIsRockNotSand() {
        assertEquals(
                SurfaceClass.ROCK,
                classifier.classify(
                        new BlockInfo(
                                1,
                                "rock-sandstone"
                        ),
                        BlockInfo.unknown(0)
                )
        );
    }

    @Test
    void looseFlintIsRock() {
        assertEquals(
                SurfaceClass.ROCK,
                classifier.classify(
                        new BlockInfo(
                                1,
                                "looseflints-basalt-free"
                        ),
                        BlockInfo.unknown(0)
                )
        );
    }

    @Test
    void stalagmiteIsRock() {
        assertEquals(
                SurfaceClass.ROCK,
                classifier.classify(
                        new BlockInfo(
                                1,
                                "stalagsection-basalt-06"
                        ),
                        BlockInfo.unknown(0)
                )
        );
    }

    @Test
    void waterLiquidWinsOverAirSolid() {
        assertEquals(
                SurfaceClass.WATER,
                classifier.classify(
                        new BlockInfo(
                                0,
                                "air"
                        ),
                        new BlockInfo(
                                7,
                                "water-still-7"
                        )
                )
        );
    }

    @Test
    void unknownModdedSurfaceRemainsUnknown() {
        assertEquals(
                SurfaceClass.UNKNOWN,
                classifier.classify(
                        new BlockInfo(
                                99,
                                "othermod:mystery-surface"
                        ),
                        BlockInfo.unknown(0)
                )
        );
    }

    @Test
    void compactRegistryLookupPreservesZeroMissingAndWaterSemantics() {
        SurfaceRegistryLookup lookup = new SurfaceRegistryLookup(Map.of(
                0, new BlockInfo(0, "game:water-looking-air"),
                7, new BlockInfo(7, "water-still-7"),
                99, new BlockInfo(99, "othermod:mystery-surface"),
                100, new BlockInfo(100, "othermod:fern-leaf")
        ));

        assertTrue(lookup.isAir(0));
        assertFalse(lookup.isWater(0));
        assertEquals(SurfaceClass.UNKNOWN, lookup.classify(0, 0));
        assertEquals(SurfaceClass.UNKNOWN, lookup.classify(404, 0));
        assertEquals(SurfaceClass.WATER, lookup.classify(0, 7));
        assertEquals(SurfaceClass.UNKNOWN, lookup.classify(99, 0));
        assertTrue(lookup.isFoliage(100));
        assertEquals(SurfaceClass.VEGETATION, lookup.classify(100, 0));
    }

    @Test
    void compactRegistryLookupPreservesAirAndFoliageFlagsForMissingAndKnownIds() {
        SurfaceRegistryLookup lookup = new SurfaceRegistryLookup(Map.of(
                100, new BlockInfo(100, "othermod:fern-leaf")));

        assertTrue(lookup.isAir(0));
        assertFalse(lookup.isAir(101));
        assertTrue(lookup.isFoliage(100));
        assertEquals(SurfaceClass.VEGETATION, lookup.classify(100, 0));
    }
}
