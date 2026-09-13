package cartographer.prospecting;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ProspectingEvaluator {
    public ProspectingAssessment assess(
            ProspectingCandidate candidate,
            OreRockCompatibilityProvider compatibilityProvider
    ) {
        Objects.requireNonNull(candidate, "prospecting candidate is required");
        Objects.requireNonNull(
                compatibilityProvider,
                "compatibility provider is required"
        );

        ProspectingEvidence evidence = candidate.evidence();
        OreRockCompatibility compatibility = compatibility(evidence, candidate, compatibilityProvider);
        ProspectingRank rank;
        List<String> reasons = new ArrayList<>();

        if (evidence.actualOreObserved()) {
            rank = ProspectingRank.CONFIRMED;
            reasons.add("actual saved ore block observed");
        } else if (evidence.geologyState() == RockColumnState.UNAVAILABLE) {
            rank = ProspectingRank.UNKNOWN;
            reasons.add("observed geology is unavailable");
        } else if (evidence.hasPositiveWorldgenSignal()
                && compatibility == OreRockCompatibility.COMPATIBLE) {
            rank = ProspectingRank.STRONG;
            reasons.add("positive worldgen signal has compatible observed geology");
        } else if (evidence.hasPositiveWorldgenSignal()
                && compatibility == OreRockCompatibility.INCOMPATIBLE) {
            rank = ProspectingRank.WEAK;
            reasons.add("positive worldgen signal has incompatible observed geology");
        } else if (evidence.hasPositiveWorldgenSignal()
                || compatibility == OreRockCompatibility.COMPATIBLE) {
            rank = ProspectingRank.INTERESTING;
            reasons.add(
                    evidence.hasPositiveWorldgenSignal()
                            ? "positive worldgen signal is present"
                            : "compatible observed geology is present"
            );
        } else {
            rank = ProspectingRank.UNKNOWN;
            reasons.add("no positive supporting evidence is available");
        }

        if (evidence.worldgenSignal().isPresent()) {
            reasons.add("worldgen signal=" + evidence.worldgenSignal().getAsDouble());
        } else {
            reasons.add("worldgen signal unavailable");
        }
        reasons.add("observed geology=" + evidence.geologyState());
        reasons.add(
                "geology coverage: observed=" + evidence.observedGeologyColumns()
                        + ", no-rock=" + evidence.noRockGeologyColumns()
                        + ", unavailable=" + evidence.unavailableGeologyColumns()
        );
        if (evidence.partialGeologyCoverage()) {
            reasons.add("observed geology coverage is partial");
        }
        reasons.add("host compatibility=" + compatibility);
        if (evidence.actualOreObservation() == ActualOreObservation.NOT_OBSERVED) {
            reasons.add("actual ore scan completed without an observation");
        } else if (evidence.actualOreObservation() == ActualOreObservation.UNAVAILABLE) {
            reasons.add("actual ore observation is unavailable");
        }
        return new ProspectingAssessment(candidate, compatibility, rank, reasons);
    }

    public List<ProspectingAssessment> assessAll(
            Collection<ProspectingCandidate> candidates,
            OreRockCompatibilityProvider compatibilityProvider
    ) {
        Objects.requireNonNull(candidates, "prospecting candidates are required");
        List<ProspectingAssessment> result = candidates.stream()
                .map(candidate -> assess(candidate, compatibilityProvider))
                .sorted(
                        Comparator.comparing(
                                        (ProspectingAssessment assessment) -> assessment.rank().ordinal()
                                )
                                .thenComparing(assessment -> assessment.candidate().resourceKey())
                )
                .toList();
        return List.copyOf(result);
    }

    private OreRockCompatibility compatibility(
            ProspectingEvidence evidence,
            ProspectingCandidate candidate,
            OreRockCompatibilityProvider provider
    ) {
        if (evidence.observedHostRocks().isEmpty()) {
            return OreRockCompatibility.UNKNOWN;
        }
        boolean unknown = false;
        for (RockIdentity rock : evidence.observedHostRocks()) {
            OreRockCompatibility result = Objects.requireNonNull(
                    provider.compatibility(candidate.resourceKey(), rock),
                    "compatibility provider returned null"
            );
            if (result == OreRockCompatibility.COMPATIBLE) {
                return result;
            }
            unknown |= result == OreRockCompatibility.UNKNOWN;
        }
        return unknown
                ? OreRockCompatibility.UNKNOWN
                : OreRockCompatibility.INCOMPATIBLE;
    }
}
