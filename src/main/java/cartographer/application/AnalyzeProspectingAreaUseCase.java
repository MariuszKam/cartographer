package cartographer.application;

import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.prospecting.ActualOreObservationProvider;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.ProspectingCandidate;
import cartographer.prospecting.ProspectingEvidence;
import cartographer.prospecting.ProspectingEvaluator;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceOverlayCell;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

public final class AnalyzeProspectingAreaUseCase {
    private final VcdbsReader reader;
    private final RenderRockMapUseCase rockMapUseCase;
    private final ResourceAnalyzer resourceAnalyzer;
    private final ProspectingEvaluator evaluator;
    private final OreRockCompatibilityProvider compatibilityProvider;
    private final ActualOreObservationProvider actualOreProvider;

    public AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            RenderRockMapUseCase rockMapUseCase,
            ResourceAnalyzer resourceAnalyzer
    ) {
        this(
                reader,
                rockMapUseCase,
                resourceAnalyzer,
                OreRockCompatibilityProvider.unknown(),
                ActualOreObservationProvider.none()
        );
    }

    public AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            RenderRockMapUseCase rockMapUseCase,
            ResourceAnalyzer resourceAnalyzer,
            OreRockCompatibilityProvider compatibilityProvider,
            ActualOreObservationProvider actualOreProvider
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.rockMapUseCase = Objects.requireNonNull(
                rockMapUseCase,
                "rock map use case is required"
        );
        this.resourceAnalyzer = Objects.requireNonNull(
                resourceAnalyzer,
                "resource analyzer is required"
        );
        this.evaluator = new ProspectingEvaluator();
        this.compatibilityProvider = Objects.requireNonNull(
                compatibilityProvider,
                "compatibility provider is required"
        );
        this.actualOreProvider = Objects.requireNonNull(
                actualOreProvider,
                "actual ore provider is required"
        );
    }

    public ProspectingAreaResult execute(ProspectingAreaRequest request) {
        Objects.requireNonNull(request, "prospecting request is required");
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(request.savePath())
        );
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ServerMapRegion> regions = reader.readMapRegions(
                request.savePath(),
                diagnostics
        );
        RockMap rockMap = rockMapUseCase.execute(
                new RenderRockMapRequest(
                        request.savePath(),
                        cartographer.geology.rock.RockMapMode.UPPER_ROCK,
                        request.radius(),
                        Optional.of(center),
                        java.util.OptionalInt.empty(),
                        java.util.OptionalInt.empty(),
                        java.util.OptionalInt.empty()
                )
        ).map();
        RockEvidence geology = geology(rockMap);
        List<String> resources = resources(regions, request.resource());
        List<ProspectingCandidate> candidates = new ArrayList<>();
        for (String resource : resources) {
            OptionalDouble signal = signal(
                    regions,
                    resource,
                    center,
                    request.radius()
            );
            candidates.add(
                    new ProspectingCandidate(
                            resource,
                            new ProspectingEvidence(
                                    signal,
                                    geology.state(),
                                    geology.rocks(),
                                    actualOreProvider.observed(
                                            resource,
                                            request.savePath(),
                                            center,
                                            request.radius()
                                    )
                            )
                    )
            );
        }
        List<ProspectingAssessment> assessments = evaluator.assessAll(
                candidates,
                compatibilityProvider
        );
        return new ProspectingAreaResult(center, request.radius(), assessments);
    }

    private List<String> resources(
            List<ServerMapRegion> regions,
            Optional<String> requested
    ) {
        if (requested.isEmpty()) {
            return resourceAnalyzer.resourceKeys(regions);
        }
        return resourceAnalyzer.matchingKeys(regions, requested.orElseThrow());
    }

    private OptionalDouble signal(
            List<ServerMapRegion> regions,
            String resource,
            WorldPosition center,
            int radius
    ) {
        List<ResourceOverlayCell> cells = resourceAnalyzer.overlayCells(
                regions,
                resource,
                0.0
        );
        OptionalDouble maximum = cells.stream()
                .filter(cell -> inArea(cell, center, radius))
                .mapToDouble(ResourceOverlayCell::relativeIntensity)
                .max();
        return maximum;
    }

    private boolean inArea(
            ResourceOverlayCell cell,
            WorldPosition center,
            int radius
    ) {
        double x = (cell.worldMinX() + cell.worldMaxX()) / 2.0;
        double z = (cell.worldMinZ() + cell.worldMaxZ()) / 2.0;
        double dx = x - center.x();
        double dz = z - center.z();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private RockEvidence geology(RockMap map) {
        Set<cartographer.geology.rock.RockIdentity> rocks = new TreeSet<>(
                Comparator.comparing(cartographer.geology.rock.RockIdentity::code)
        );
        boolean unavailable = false;
        for (RockColumnSample sample : map.columns()) {
            if (sample.state() == RockColumnState.UNAVAILABLE) {
                unavailable = true;
            } else if (sample.state() == RockColumnState.OBSERVED) {
                sample.rock().ifPresent(rocks::add);
            }
        }
        RockColumnState state = unavailable
                ? RockColumnState.UNAVAILABLE
                : rocks.isEmpty()
                ? RockColumnState.NO_ROCK
                : RockColumnState.OBSERVED;
        return new RockEvidence(state, List.copyOf(rocks));
    }

    private record RockEvidence(
            RockColumnState state,
            List<cartographer.geology.rock.RockIdentity> rocks
    ) {
    }
}
