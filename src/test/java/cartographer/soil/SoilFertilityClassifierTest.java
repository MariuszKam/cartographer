package cartographer.soil;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilFertilityClassifierTest {
    private final SoilFertilityClassifier classifier = new SoilFertilityClassifier();

    @Test
    void exposesNominalFertilityPercentages() {
        assertEquals(0, SoilFertilityTier.BONY.fertilityPercent());
        assertEquals(5, SoilFertilityTier.BARREN.fertilityPercent());
        assertEquals(25, SoilFertilityTier.LOW.fertilityPercent());
        assertEquals(50, SoilFertilityTier.MEDIUM.fertilityPercent());
        assertEquals(65, SoilFertilityTier.HIGH.fertilityPercent());
        assertEquals(80, SoilFertilityTier.TERRA_PRETA.fertilityPercent());
    }

    @Test
    void classifiesKnownBonySoilVariants() {
        Stream.concat(Stream.of("bonysoil"), IntStream.rangeClosed(1, 7)
                        .mapToObj(number -> "bonysoil-" + number))
                .forEach(code -> assertClassification(
                        code,
                        SoilFertilityTier.BONY,
                        SoilFertilitySource.BONY_SOIL
                ));
    }

    @Test
    void classifiesKnownForestFloorVariants() {
        IntStream.rangeClosed(0, 7)
                .mapToObj(number -> "forestfloor-" + number)
                .forEach(code -> assertClassification(
                        code,
                        SoilFertilityTier.LOW,
                        SoilFertilitySource.FOREST_FLOOR
                ));
    }

    @Test
    void classifiesAllKnownOrdinarySoilCombinations() {
        Map<String, SoilFertilityTier> fertility = Map.of(
                "verylow", SoilFertilityTier.BARREN,
                "low", SoilFertilityTier.LOW,
                "medium", SoilFertilityTier.MEDIUM,
                "high", SoilFertilityTier.HIGH,
                "compost", SoilFertilityTier.TERRA_PRETA
        );
        String[] variants = {"none", "verysparse", "sparse", "normal"};

        fertility.forEach((token, tier) -> Stream.of(variants)
                .map(variant -> "soil-" + token + "-" + variant)
                .forEach(code -> assertClassification(
                        code,
                        tier,
                        SoilFertilitySource.SOIL
                )));
    }

    @Test
    void classifiesAllKnownFarmlandCombinations() {
        Map<String, SoilFertilityTier> fertility = Map.of(
                "verylow", SoilFertilityTier.BARREN,
                "low", SoilFertilityTier.LOW,
                "medium", SoilFertilityTier.MEDIUM,
                "high", SoilFertilityTier.HIGH,
                "compost", SoilFertilityTier.TERRA_PRETA
        );

        fertility.forEach((token, tier) -> Stream.of("dry", "moist")
                .map(moisture -> "farmland-" + moisture + "-" + token)
                .forEach(code -> assertClassification(
                        code,
                        tier,
                        SoilFertilitySource.FARMLAND
                )));
    }

    @Test
    void appliesConservativeNamespacePolicyAndPreservesEvidence() {
        SoilFertilityClassification unqualified = classify("soil-medium-normal");
        SoilFertilityClassification qualified = classify("GAME:SOIL-MEDIUM-NORMAL");

        assertEquals("soil-medium-normal", unqualified.normalizedPath());
        assertEquals("GAME:SOIL-MEDIUM-NORMAL", qualified.originalCode());
        assertEquals("soil-medium-normal", qualified.normalizedPath());
        assertTrue(classifyOptional("somemod:soil-high-normal").isEmpty());
        assertTrue(classifyOptional(":soil-medium-normal").isEmpty());
        assertTrue(classifyOptional("game:").isEmpty());
        assertTrue(classifyOptional("game:foo:soil-medium-normal").isEmpty());
    }

    @Test
    void classifiesBlockInfoAndPreservesOriginalCode() {
        BlockInfo block = new BlockInfo(42, "  Game:Farmland-Moist-High  ");

        SoilFertilityClassification classification = classifier.classify(block)
                .orElseThrow();

        assertEquals(block.code(), classification.originalCode());
        assertEquals("farmland-moist-high", classification.normalizedPath());
        assertEquals(SoilFertilityTier.HIGH, classification.tier());
        assertEquals(SoilFertilitySource.FARMLAND, classification.source());
    }

    @Test
    void returnsEmptyForNullBlankFalsePositiveAndMalformedCodes() {
        Stream.of(
                        "creativegrass-medium-normal",
                        "packeddirt",
                        "drypackeddirt",
                        "peat-none",
                        "peat-verysparse",
                        "clay",
                        "fireclay",
                        "cob",
                        "mud",
                        "rock-granite",
                        "ore-nativecopper-granite",
                        "soil-high",
                        "soil-high-dense",
                        "soil--normal",
                        "bonysoil-8",
                        "forestfloor-8",
                        "farmland-wet-high",
                        "farmland-moist-superhigh",
                        "crop-flax-9",
                        "deadcrop",
                        "snowlayer-1",
                        "snowlayer-7",
                        "snowblock",
                        "water-still-7",
                        "saltwater-still-7",
                        "somemod:soil-high-normal"
                )
                .forEach(code -> assertTrue(classifyOptional(code).isEmpty(), code));

        assertTrue(classifyOptional(null).isEmpty());
        assertTrue(classifyOptional("   ").isEmpty());
        assertTrue(classifier.classify(null).isEmpty());
    }

    private void assertClassification(
            String code,
            SoilFertilityTier expectedTier,
            SoilFertilitySource expectedSource
    ) {
        SoilFertilityClassification classification = classify(code);
        assertEquals(expectedTier, classification.tier(), code);
        assertEquals(expectedSource, classification.source(), code);
        assertEquals(expectedTier.fertilityPercent(), classification.tier().fertilityPercent());
    }

    private Optional<SoilFertilityClassification> classifyOptional(String code) {
        return classifier.classify(new BlockInfo(1, code));
    }

    private SoilFertilityClassification classify(String code) {
        Optional<SoilFertilityClassification> result = classifyOptional(code);
        return result.orElseThrow(() -> new AssertionError("Expected classification: " + code));
    }
}
