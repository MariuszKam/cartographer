package cartographer.application;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;
import cartographer.geology.rock.RockMap;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.prospecting.ActualOreObservationProvider;
import cartographer.prospecting.ActualOreObservation;
import cartographer.prospecting.FusedProspectingObservationProvider;
import cartographer.prospecting.FusedProspectingResult;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.ProspectingCandidate;
import cartographer.prospecting.ProspectingEvidence;
import cartographer.prospecting.ProspectingEvaluator;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceOverlayCell;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class AnalyzeProspectingAreaUseCase {
    private final VcdbsReader reader;
    private final RenderRockMapUseCase rockMapUseCase;
    private final ResourceAnalyzer resourceAnalyzer;
    private final ProspectingEvaluator evaluator;
    private final OreRockCompatibilityProvider compatibilityProvider;
    private final ActualOreObservationProvider actualOreProvider;
    private final SaveSessionFactory sessionFactory;

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
        this(
                reader,
                rockMapUseCase,
                resourceAnalyzer,
                compatibilityProvider,
                actualOreProvider,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        new WorldMetadataReader()
                )
        );
    }

    public AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            RenderRockMapUseCase rockMapUseCase,
            ResourceAnalyzer resourceAnalyzer,
            OreRockCompatibilityProvider compatibilityProvider,
            ActualOreObservationProvider actualOreProvider,
            SaveSessionFactory sessionFactory
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
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
    }

    public ProspectingAreaResult execute(ProspectingAreaRequest request) {
        Objects.requireNonNull(request, "prospecting request is required");
        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            return execute(saveSession, request);
        }
    }

    public ProspectingAreaResult execute(
            SaveSession saveSession,
            ProspectingAreaRequest request
    ) {
        Objects.requireNonNull(saveSession, "save session is required");
        Objects.requireNonNull(request, "prospecting request is required");
        saveSession.requireSameSave(request.savePath());
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(saveSession, ProgressReporter.NONE)
        );
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        List<ServerMapRegion> regions = reader.readMapRegions(
                saveSession,
                diagnostics,
                ProgressReporter.NONE
        );
        List<String> resources = resources(regions, request.resource());
        FusedProspectingResult fused = actualOreProvider instanceof FusedProspectingObservationProvider provider
                ? provider.analyze(saveSession, center, request.radius(), resources)
                : null;
        RockEvidence geology = fused == null
                ? geology(rockMapUseCase.execute(
                        saveSession,
                        new RenderRockMapRequest(
                                request.savePath(),
                                cartographer.geology.rock.RockMapMode.UPPER_ROCK,
                                request.radius(),
                                Optional.of(center),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalInt.empty()
                        ),
                        ProgressReporter.NONE
                ).map())
                : geology(fused.rockMap());
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
                                    fused == null
                                            ? actualOreProvider.observation(resource, request.savePath(), center, request.radius())
                                            : fused.observation(resource),
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
        int observedColumns = Math.toIntExact(map.observedCount());
        int noRockColumns = Math.toIntExact(map.noRockCount());
        int unavailableColumns = Math.toIntExact(map.unavailableCount());
        long[] counts = map.countsByOrdinal();
        List<RockIdentity> rocks = new ArrayList<>();
        List<RockIdentity> ordinalTable = map.ordinalTable();
        for (int ordinal = 1; ordinal <= ordinalTable.size(); ordinal++) {
            if (counts[ordinal] > 0) rocks.add(ordinalTable.get(ordinal - 1));
        }
        rocks.sort(java.util.Comparator.comparing(RockIdentity::code));
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
