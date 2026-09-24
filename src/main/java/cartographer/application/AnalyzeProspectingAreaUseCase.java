package cartographer.application;

import cartographer.geology.rock.RockColumnState;
import cartographer.geology.rock.RockIdentity;
import cartographer.geology.rock.RockMap;
import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.prospecting.FusedProspectingObservationProvider;
import cartographer.prospecting.FusedProspectingResult;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.ProspectingCandidate;
import cartographer.prospecting.ProspectingEvidence;
import cartographer.prospecting.ProspectingEvaluator;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.ResourceOverlayCell;
import cartographer.perf.RenderDataCacheStore;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.SnapshotMapRegionReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class AnalyzeProspectingAreaUseCase {
    private final VcdbsReader reader;
    private final ResourceAnalyzer resourceAnalyzer;
    private final ProspectingEvaluator evaluator;
    private final OreRockCompatibilityProvider compatibilityProvider;
    private final FusedProspectingObservationProvider prospectingProvider;
    private final SaveSessionFactory sessionFactory;
    private final Optional<SnapshotMapRegionReader> snapshotMapRegionReader;
    private final Optional<SnapshotWorldHeaderReader> snapshotHeaderReader;

    public AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer,
            OreRockCompatibilityProvider compatibilityProvider,
            FusedProspectingObservationProvider prospectingProvider,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                resourceAnalyzer,
                compatibilityProvider,
                prospectingProvider,
                sessionFactory,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer,
            OreRockCompatibilityProvider compatibilityProvider,
            FusedProspectingObservationProvider prospectingProvider,
            SaveSessionFactory sessionFactory
    ) {
        this(
                reader,
                resourceAnalyzer,
                compatibilityProvider,
                prospectingProvider,
                sessionFactory,
                Optional.empty()
        );
    }

    AnalyzeProspectingAreaUseCase(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer,
            OreRockCompatibilityProvider compatibilityProvider,
            FusedProspectingObservationProvider prospectingProvider,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.resourceAnalyzer = Objects.requireNonNull(
                resourceAnalyzer,
                "resource analyzer is required"
        );
        this.evaluator = new ProspectingEvaluator();
        this.compatibilityProvider = Objects.requireNonNull(
                compatibilityProvider,
                "compatibility provider is required"
        );
        this.prospectingProvider = Objects.requireNonNull(
                prospectingProvider,
                "prospecting provider is required"
        );
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
        Optional<RenderDataCacheStore> cache =
                Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache option is required"
                );
        this.snapshotMapRegionReader =
                cache.map(SnapshotMapRegionReader::new);
        this.snapshotHeaderReader =
                cache.map(SnapshotWorldHeaderReader::new);
    }

    public ProspectingAreaResult execute(ProspectingAreaRequest request) {
        Objects.requireNonNull(request, "prospecting request is required");

        Optional<ProspectingAreaResult> snapshot =
                executeSnapshot(request);
        if (snapshot.isPresent()) {
            return snapshot.orElseThrow();
        }

        try (SaveSession saveSession = sessionFactory.open(request.savePath())) {
            return execute(saveSession, request);
        }
    }

    private Optional<ProspectingAreaResult> executeSnapshot(
            ProspectingAreaRequest request
    ) {
        if (snapshotMapRegionReader.isEmpty()
                || snapshotHeaderReader.isEmpty()) {
            return Optional.empty();
        }

        var header = snapshotHeaderReader.orElseThrow()
                .read(request.savePath());
        var mapRegions = snapshotMapRegionReader.orElseThrow()
                .read(request.savePath());
        if (header.isEmpty() || mapRegions.isEmpty()) {
            return Optional.empty();
        }

        WorldPosition center;
        if (request.center().isPresent()) {
            center = request.center().orElseThrow();
        } else if (header.orElseThrow().player().isPresent()) {
            center = header.orElseThrow().player().orElseThrow();
        } else {
            return Optional.empty();
        }

        List<ServerMapRegion> regions =
                mapRegions.orElseThrow().resourceRegions();
        List<String> resources =
                resources(regions, request.resources());
        FusedProspectingResult fused = prospectingProvider.analyze(
                request.savePath(),
                center,
                request.radius(),
                resources
        );
        return Optional.of(buildResult(
                request,
                center,
                regions,
                resources,
                fused
        ));
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
        List<ServerMapRegion> regions =
                snapshotMapRegionReader
                        .flatMap(reader -> reader.read(request.savePath()))
                        .map(SnapshotMapRegionReader.Result::resourceRegions)
                        .orElseGet(() -> {
                            ReadDiagnostics diagnostics =
                                    new ReadDiagnostics();
                            return reader.readMapRegions(
                                    saveSession,
                                    diagnostics,
                                    ProgressReporter.NONE
                            );
                        });
        List<String> resources = resources(regions, request.resources());
        FusedProspectingResult fused = prospectingProvider.analyze(
                saveSession,
                center,
                request.radius(),
                resources
        );
        return buildResult(
                request,
                center,
                regions,
                resources,
                fused
        );
    }

    private ProspectingAreaResult buildResult(
            ProspectingAreaRequest request,
            WorldPosition center,
            List<ServerMapRegion> regions,
            List<String> resources,
            FusedProspectingResult fused
    ) {
        RockEvidence geology = geology(fused.rockMap());
        List<ProspectingCandidate> candidates = new ArrayList<>();
        for (String resource : resources) {
            OptionalDouble signal = signal(
                    regions,
                    resource,
                    center,
                    request.radius()
            );
            candidates.add(new ProspectingCandidate(
                    resource,
                    new ProspectingEvidence(
                            signal,
                            geology.state(),
                            geology.rocks(),
                            fused.observation(resource),
                            geology.observedColumns(),
                            geology.noRockColumns(),
                            geology.unavailableColumns()
                    )
            ));
        }
        List<ProspectingAssessment> assessments = evaluator.assessAll(
                candidates,
                compatibilityProvider
        );
        return new ProspectingAreaResult(
                center,
                request.radius(),
                assessments,
                Optional.of(fused.rockMap())
        );
    }

    private List<String> resources(
            List<ServerMapRegion> regions,
            List<String> requested
    ) {
        if (requested.isEmpty()) {
            return resourceAnalyzer.resourceKeys(regions);
        }
        java.util.LinkedHashSet<String> matched = new java.util.LinkedHashSet<>();
        for (String resource : requested) {
            matched.addAll(resourceAnalyzer.matchingKeys(regions, resource));
        }
        return List.copyOf(matched);
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
