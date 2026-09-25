package cartographer.prospecting;

import cartographer.resource.ActualOreObservation;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProspectingEvaluatorTest {
    private static final RockIdentity GRANITE = new RockIdentity(
            7,
            "game:rock-granite",
            "game",
            "granite"
    );
    private static final RockIdentity GNEISS = new RockIdentity(
            8,
            "somemod:rock-gneiss",
            "somemod",
            "gneiss"
    );

    private final ProspectingEvaluator evaluator = new ProspectingEvaluator();

    @Test
    void actualOreObservationIsConfirmed() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.empty(), RockColumnState.UNAVAILABLE, List.of(), true),
                OreRockCompatibilityProvider.unknown()
        );

        assertEquals(ProspectingRank.CONFIRMED, assessment.rank());
    }

    @Test
    void positiveSignalAndCompatibleRockAreStrong() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.of(0.8), RockColumnState.OBSERVED, List.of(GRANITE), false),
                (resource, rock) -> OreRockCompatibility.COMPATIBLE
        );

        assertEquals(ProspectingRank.STRONG, assessment.rank());
        assertEquals(OreRockCompatibility.COMPATIBLE, assessment.compatibility());
    }

    @Test
    void incompatibleRockLeavesSignalWeak() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.of(0.8), RockColumnState.OBSERVED, List.of(GRANITE), false),
                (resource, rock) -> OreRockCompatibility.INCOMPATIBLE
        );

        assertEquals(ProspectingRank.WEAK, assessment.rank());
    }

    @Test
    void unknownCompatibilityIsInterestingRatherThanIncompatible() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("modore", OptionalDouble.of(0.8), RockColumnState.OBSERVED, List.of(GNEISS), false),
                OreRockCompatibilityProvider.unknown()
        );

        assertEquals(ProspectingRank.INTERESTING, assessment.rank());
        assertEquals(OreRockCompatibility.UNKNOWN, assessment.compatibility());
    }

    @Test
    void compatibleRockWithoutSignalIsInteresting() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.empty(), RockColumnState.OBSERVED, List.of(GRANITE), false),
                (resource, rock) -> OreRockCompatibility.COMPATIBLE
        );

        assertEquals(ProspectingRank.INTERESTING, assessment.rank());
    }

    @Test
    void noRockIsAvailableEvidenceNotUnavailable() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.of(0.4), RockColumnState.NO_ROCK, List.of(), false),
                OreRockCompatibilityProvider.unknown()
        );

        assertEquals(ProspectingRank.INTERESTING, assessment.rank());
        assertTrue(assessment.reasons().contains("observed geology=NO_ROCK"));
    }

    @Test
    void unavailableGeologyProducesUnknown() {
        ProspectingAssessment assessment = evaluator.assess(
                candidate("cassiterite", OptionalDouble.of(0.9), RockColumnState.UNAVAILABLE, List.of(), false),
                (resource, rock) -> OreRockCompatibility.COMPATIBLE
        );

        assertEquals(ProspectingRank.UNKNOWN, assessment.rank());
        assertEquals(OreRockCompatibility.UNKNOWN, assessment.compatibility());
    }

    @Test
    void partialGeologyStillUsesObservedCompatibleRock() {
        ProspectingAssessment assessment = evaluator.assess(
                new ProspectingCandidate(
                        "cassiterite",
                        new ProspectingEvidence(
                                OptionalDouble.of(0.8),
                                RockColumnState.OBSERVED,
                                List.of(GRANITE),
                                ActualOreObservation.NOT_OBSERVED,
                                100,
                                20,
                                1
                        )
                ),
                (resource, rock) -> OreRockCompatibility.COMPATIBLE
        );

        assertEquals(ProspectingRank.STRONG, assessment.rank());
        assertTrue(assessment.reasons().contains("observed geology coverage is partial"));
    }

    @Test
    void unavailableActualOreScanIsNotReportedAsNotObserved() {
        ProspectingAssessment assessment = evaluator.assess(
                new ProspectingCandidate(
                        "cassiterite",
                        new ProspectingEvidence(
                                OptionalDouble.empty(),
                                RockColumnState.UNAVAILABLE,
                                List.of(),
                                ActualOreObservation.UNAVAILABLE,
                                0,
                                0,
                                4
                        )
                ),
                OreRockCompatibilityProvider.unknown()
        );

        assertEquals(ActualOreObservation.UNAVAILABLE, assessment.candidate().evidence().actualOreObservation());
        assertTrue(assessment.reasons().contains("actual ore observation is unavailable"));
    }

    @Test
    void assessmentsAreOrderedByRankThenResourceKey() {
        List<ProspectingAssessment> result = evaluator.assessAll(
                List.of(
                        candidate("zinc", OptionalDouble.of(0.2), RockColumnState.NO_ROCK, List.of(), false),
                        candidate("copper", OptionalDouble.empty(), RockColumnState.OBSERVED, List.of(GRANITE), false),
                        candidate("gold", OptionalDouble.empty(), RockColumnState.UNAVAILABLE, List.of(), true)
                ),
                OreRockCompatibilityProvider.unknown()
        );

        assertEquals(List.of("gold", "zinc", "copper"), result.stream()
                .map(assessment -> assessment.candidate().resourceKey())
                .toList());
    }

    private ProspectingCandidate candidate(
            String resource,
            OptionalDouble signal,
            RockColumnState geology,
            List<RockIdentity> rocks,
            boolean actualOre
    ) {
        return new ProspectingCandidate(
                resource,
                new ProspectingEvidence(signal, geology, rocks, actualOre)
        );
    }
}
