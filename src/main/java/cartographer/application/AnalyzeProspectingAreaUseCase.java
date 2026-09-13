package cartographer.application;

import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockMap;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.prospecting.ActualOreObservationProvider;
import cartographer.prospecting.ActualOreObservation;
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
                                    actualOreProvider.observation(
                                            resource,
                                            request.savePath(),
                                            center,
                                            request.radius()
                                    ),
                                    geology.observedColumns(),
                                    geology.noRockColumns(),
                                    geology.unavailableColumns()
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
        int observedColumns = 0;
        int noRockColumns = 0;
        int unavailableColumns = 0;
        for (RockColumnSample sample : map.columns()) {
            if (sample.state() == RockColumnState.UNAVAILABLE) {
                unavailableColumns++;
            } else if (sample.state() == RockColumnState.OBSERVED) {
                observedColumns++;
                sample.rock().ifPresent(rocks::add);
            } else {
                noRockColumns++;
            }
        }
        RockColumnState state = observedColumns > 0
                ? RockColumnState.OBSERVED
                : unavailableColumns > 0
                ? RockColumnState.UNAVAILABLE
                : RockColumnState.NO_ROCK;
        return new RockEvidence(
                state,
                List.copyOf(rocks),
                observedColumns,
                noRockColumns,
                unavailableColumns
        );
    }

    private record RockEvidence(
            RockColumnState state,
            List<cartographer.geology.rock.RockIdentity> rocks,
            int observedColumns,
            int noRockColumns,
            int unavailableColumns
    ) {
    }
}
