package cartographer.ui;

import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.DiscoverObservedSurfaceResourcesRequest;
import cartographer.application.DiscoverObservedSurfaceResourcesResult;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.LoadWorldOverviewUseCase;
import cartographer.application.InspectWorldSnapshotStatusUseCase;
import cartographer.application.PrepareWorldSnapshotRequest;
import cartographer.application.PrepareWorldSnapshotResult;
import cartographer.application.PrepareWorldSnapshotUseCase;
import cartographer.application.WorldSnapshotStatus;
import cartographer.application.ProspectingAreaRequest;
import cartographer.application.ProspectingAreaResult;
import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.RenderCoverageMapRequest;
import cartographer.application.RenderCoverageMapResult;
import cartographer.application.RenderCoverageMapUseCase;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.application.RenderSurfaceResourceMapRequest;
import cartographer.application.RenderSurfaceResourceMapResult;
import cartographer.application.RenderSurfaceResourceMapUseCase;
import cartographer.application.SurfaceDiscoveryCache;
import cartographer.application.SurfaceDiscoveryCacheKey;
import cartographer.application.SurfaceDiscoveryPolicy;
import cartographer.application.SurfaceDiscoveryRequestGate;
import cartographer.application.SurfaceMaterialMatch;
import cartographer.application.WorldOverview;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.RenderStyle;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.ui.workstation.MapCursorPosition;
import cartographer.ui.workstation.LocalRecompositionGate;
import cartographer.ui.workstation.MapFrame;
import cartographer.ui.workstation.MapFrameCompositor;
import cartographer.ui.workstation.MapFrameState;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.ResultInspectorPane;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.SurfaceObjectDiscoveryState;
import cartographer.ui.workstation.SurfaceToolMode;
import cartographer.ui.workstation.WorkstationOperationCoordinator;
import cartographer.ui.workstation.WorkstationOperationScope;
import cartographer.ui.workstation.WorkstationTool;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.update.UpdateCheckView;
import cartographer.ui.workstation.WorldPanel;
import javafx.concurrent.Task;
import javafx.scene.Parent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

/**
 * JavaFX Workstation orchestration layer.
 *
 * <p>The desktop application owns dependency construction and Stage/FileChooser setup.
 * This controller owns Workstation state, request assembly, background-operation
 * orchestration and result presentation.</p>
 */
public final class WorkstationController {
    private final Supplier<Optional<Path>> saveChooser;
    private final WorkstationView workstation;
    private final SearchPanel searchPanel;
    private final WorldPanel worldPanel;
    private final MapPanel mapPanel;
    private final ResultInspectorPane resultInspector;
    private final WorkstationOperationCoordinator operationCoordinator;
    private final MapFrameState mapFrameState = new MapFrameState();
    private final MapFrameCompositor mapFrameCompositor = new MapFrameCompositor();
    private final LocalRecompositionGate localRecompositionGate =
            new LocalRecompositionGate();
    private long rockHighlightGeneration;

    private final RenderActualOreMapUseCase useCase;
    private final RenderCoverageMapUseCase coverageUseCase;
    private final RenderSurfaceResourceMapUseCase surfaceUseCase;
    private final DiscoverObservedSurfaceResourcesUseCase surfaceDiscoveryUseCase;
    private final RenderRockMapUseCase rockUseCase;
    private final AnalyzeProspectingAreaUseCase prospectingUseCase;
    private final LoadWorldOverviewUseCase worldOverviewUseCase;
    private final PrepareWorldSnapshotUseCase prepareWorldSnapshotUseCase;
    private final InspectWorldSnapshotStatusUseCase snapshotStatusUseCase;
    private final OreResourceResolver resourceResolver = new OreResourceResolver();

    private DiscoverObservedSurfaceResourcesResult surfaceDiscoveryResult;
    private SurfaceObjectDiscoveryState surfaceObjectDiscoveryState =
            SurfaceObjectDiscoveryState.NOT_SCANNED;
    private final SurfaceDiscoveryRequestGate surfaceDiscoveryGate =
            new SurfaceDiscoveryRequestGate();
    private final SurfaceDiscoveryCache surfaceDiscoveryCache =
            new SurfaceDiscoveryCache(4);
    private Optional<cartographer.model.WorldPosition> surfaceDiscoveryCenter = Optional.empty();
    private Optional<cartographer.model.WorldPosition> loadedPlayerAbsolute = Optional.empty();
    private Optional<WorldMetadata> loadedWorldMetadata = Optional.empty();
    private SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey surfaceDiscoveryTaskKey;
    private Task<DiscoverObservedSurfaceResourcesResult> surfaceDiscoveryTask;
    private Set<String> surfaceSelectionKeys = Set.of();

    public WorkstationController(
            Supplier<Optional<Path>> saveChooser,
            RenderActualOreMapUseCase useCase,
            RenderCoverageMapUseCase coverageUseCase,
            RenderSurfaceResourceMapUseCase surfaceUseCase,
            DiscoverObservedSurfaceResourcesUseCase surfaceDiscoveryUseCase,
            RenderRockMapUseCase rockUseCase,
            AnalyzeProspectingAreaUseCase prospectingUseCase,
            LoadWorldOverviewUseCase worldOverviewUseCase,
            PrepareWorldSnapshotUseCase prepareWorldSnapshotUseCase,
            InspectWorldSnapshotStatusUseCase snapshotStatusUseCase
    ) {
        this.saveChooser = Objects.requireNonNull(saveChooser, "save chooser is required");
        this.useCase = Objects.requireNonNull(useCase, "map/ore use case is required");
        this.coverageUseCase = Objects.requireNonNull(coverageUseCase, "coverage use case is required");
        this.surfaceUseCase = Objects.requireNonNull(surfaceUseCase, "surface use case is required");
        this.surfaceDiscoveryUseCase = Objects.requireNonNull(
                surfaceDiscoveryUseCase, "surface discovery use case is required");
        this.rockUseCase = Objects.requireNonNull(rockUseCase, "rock use case is required");
        this.prospectingUseCase = Objects.requireNonNull(
                prospectingUseCase, "prospecting use case is required");
        this.worldOverviewUseCase = Objects.requireNonNull(
                worldOverviewUseCase, "world overview use case is required");
        this.prepareWorldSnapshotUseCase = Objects.requireNonNull(
                prepareWorldSnapshotUseCase,
                "prepare world snapshot use case is required"
        );
        this.snapshotStatusUseCase = Objects.requireNonNull(
                snapshotStatusUseCase,
                "snapshot status use case is required"
        );

        workstation = new WorkstationView(
                this::chooseSave,
                this::render,
                this::prepareWorldSnapshot
        );
        worldPanel = workstation.worldPanel();
        searchPanel = workstation.searchPanel();
        mapPanel = workstation.mapPanel();
        resultInspector = workstation.resultInspectorPane();
        operationCoordinator = new WorkstationOperationCoordinator(workstation);

        mapPanel.setOnCursorPositionChanged(this::handleCursorPositionChanged);
        workstation.setOnModeChanged(this::handleModeChanged);
        workstation.setOnRadiusChanged(this::handleRadiusChanged);
        workstation.setOnSurfaceModeChanged(this::handleSurfaceModeChanged);
        workstation.setOnRenderLayersChanged(this::handleRenderLayersChanged);
        searchPanel.setOnRockHighlightChanged(this::handleRockHighlightChanged);
        workstation.setOnCancel(this::cancelPreferredOperation);
        operationCoordinator.setOnCancelled(this::handleOperationCancelled);
    }

    public Parent root() {
        return workstation.root();
    }

    public UpdateCheckView updateCheckView() {
        return workstation;
    }

    public void shutdown() {
        operationCoordinator.cancelAll();
    }

    private void chooseSave() {
        saveChooser.get().ifPresent(savePath -> {
            worldPanel.setSavePath(savePath.toString());
            workstation.setSavePath(savePath);
            workstation.setSnapshotSaveAvailable(true);
            refreshSnapshotStatus(savePath);
            workstation.setStatus("");
            loadSaveData(savePath);
        });
    }

    private void prepareWorldSnapshot() {
        if (worldPanel.savePathText().isBlank()) {
            showFailure(
                    new IllegalArgumentException("Select a .vcdbs save.")
            );
            return;
        }
        Path savePath = Path.of(worldPanel.savePathText())
                .toAbsolutePath()
                .normalize();
        setBusy(true);
        workstation.setSnapshotPreparing(true);
        workstation.setStatus("Preparing reusable world snapshot...");

        PrepareWorldSnapshotRequest request =
                new PrepareWorldSnapshotRequest(savePath);
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "world-snapshot-prepare",
                "Prepare world " + savePath.getFileName(),
                progress -> prepareWorldSnapshotUseCase.execute(
                        request,
                        progress
                ),
                result -> showPreparedWorld(result, savePath),
                failure -> {
                    workstation.setSnapshotPreparing(false);
                    refreshSnapshotStatus(savePath);
                    showFailure(failure);
                }
        );
    }

    private void showPreparedWorld(
            PrepareWorldSnapshotResult result,
            Path savePath
    ) {
        setBusy(false);
        workstation.setSnapshotPreparing(false);
        refreshSnapshotStatus(savePath);
        resultInspector.showWorldSnapshotResult(result);
        workstation.setStatus(
                result.complete()
                        ? "World snapshot ready. Compatible renders can reuse indexed data."
                        : "World snapshot prepared with partial coverage; Prepare World can resume it."
        );
    }

    private void refreshSnapshotStatus(Path savePath) {
        try {
            WorldSnapshotStatus status =
                    snapshotStatusUseCase.execute(savePath);
            workstation.setSnapshotStatus(status);
        } catch (RuntimeException failure) {
            workstation.setSnapshotPreparing(false);
            workstation.setStatus(
                    "Snapshot status unavailable: "
                            + conciseMessage(failure)
            );
        }
    }

    private void loadSaveData(Path savePath) {
        operationCoordinator.cancelAll();
        loadedPlayerAbsolute = Optional.empty();
        loadedWorldMetadata = Optional.empty();
        workstation.setSnapshotPreparing(false);
        refreshSnapshotStatus(savePath);
        mapPanel.clearNavigationContext();
        localRecompositionGate.invalidate();
        rockHighlightGeneration++;
        mapFrameState.clear();
        workstation.clearMapGeometry();
        surfaceSelectionKeys = Set.of();
        invalidateSurfaceDiscovery();
        setBusy(true);
        workstation.setStatus("Loading resources and player position...");
        workstation.setPlayerLoaded(false);
        worldPanel.setPlayerStatus("Player: loading...");

        operationCoordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "world-overview",
                "Load " + savePath.getFileName(),
                () -> worldOverviewUseCase.execute(savePath),
                loaded -> {
                    List<OreResource> discovered = resourceResolver.resolve(
                            loaded.resourceKeys(),
                            loaded.blockRegistry()
                    );
                    searchPanel.setResources(discovered, loaded.blockRegistry());
                    loadedPlayerAbsolute = loaded.playerAbsolute();
                    loadedWorldMetadata = Optional.of(loaded.metadata());
                    Optional<PlayerPositionSnapshot> player = loaded.playerAbsolute()
                            .map(position -> playerSnapshot(loaded.metadata(), position));
                    worldPanel.setPlayerStatus(
                            player.map(snapshot -> formatPlayer(snapshot.display()))
                                    .orElse("Player: unavailable")
                    );
                    workstation.setPlayerLoaded(player.isPresent());
                    workstation.setStatus(
                            discovered.isEmpty()
                                    ? "No resource maps found; custom matches are available."
                                    : "Loaded " + discovered.size() + " resources."
                    );
                    setBusy(false);
                },
                failure -> {
                    searchPanel.setDiscoveryFailure();
                    worldPanel.setPlayerStatus("Player: unavailable");
                    workstation.setPlayerLoaded(false);
                    showFailure(failure);
                }
        );
    }

    private void render() {
        try {
            if (searchPanel.selectedMode() == WorkstationTool.PROSPECTING) {
                analyzeProspectingArea();
                return;
            }
            if (searchPanel.selectedMode() == WorkstationTool.COVERAGE) {
                renderCoverage();
                return;
            }
            if (searchPanel.selectedMode() == WorkstationTool.GEOLOGY) {
                renderRockMap();
                return;
            }
            if (searchPanel.selectedMode() == WorkstationTool.SURFACE) {
                renderSurfaceResource();
                return;
            }
            if (searchPanel.selectedMode() == WorkstationTool.MAP) {
                renderMap();
                return;
            }
            RenderActualOreMapRequest request = requestFromControls();
            boolean requireSurfaceData =
                    request.layers().contains(cartographer.render.RenderLayer.SURFACE)
                            || request.layers().contains(
                            cartographer.render.RenderLayer.SOIL_FERTILITY
                    );
            Optional<MapFrame> reusable = mapFrameState.current()
                    .filter(frame -> frame.canReusePreparedMap(
                            request.savePath(),
                            request.radius(),
                            request.pixelsPerBlock(),
                            request.style(),
                            request.center(),
                            requireSurfaceData
                    ))
                    .filter(frame -> !request.layers().contains(
                            cartographer.render.RenderLayer.MARKERS
                    ) || frame.decorationState().orElseThrow().userMarkersAvailable());
            setBusy(true);
            if (reusable.isPresent()) {
                MapFrame frame = reusable.orElseThrow();
                workstation.setStatus("Rendering ore map with retained base data...");
                operationCoordinator.submitProgress(
                        WorkstationOperationScope.FOREGROUND,
                        "ore-retained-render",
                        "Ore retained R" + request.radius(),
                        progress -> useCase.executeRetained(
                                request,
                                frame.savePath(),
                                frame.preparedMapData().orElseThrow(),
                                frame.decorationState().orElseThrow(),
                                frame.mapRegionOverlayState(),
                                progress
                        ),
                        result -> showResult(result, request),
                        this::showFailure
                );
            } else {
                workstation.setStatus("Rendering ore map...");
                operationCoordinator.submitProgress(
                        WorkstationOperationScope.FOREGROUND,
                        "ore-render",
                        "Ore R" + request.radius(),
                        progress -> useCase.execute(request, progress),
                        result -> showResult(result, request),
                        this::showFailure
                );
            }
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private void renderCoverage() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        RenderCoverageMapRequest request = new RenderCoverageMapRequest(
                Path.of(worldPanel.savePathText())
        );
        setBusy(true);
        workstation.setStatus("Rendering coverage...");
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "coverage-render",
                "Coverage " + request.savePath().getFileName(),
                progress -> coverageUseCase.execute(request, progress),
                result -> showCoverageResult(result, request),
                this::showFailure
        );
    }

    private void renderMap() {
        RenderActualOreMapRequest request = mapRequestFromControls();
        boolean requireSurfaceData =
                request.layers().contains(cartographer.render.RenderLayer.SURFACE)
                        || request.layers().contains(
                        cartographer.render.RenderLayer.SOIL_FERTILITY
                );
        Optional<MapFrame> reusable = mapFrameState.current()
                .filter(frame -> frame.canReusePreparedMap(
                        request.savePath(),
                        request.radius(),
                        request.pixelsPerBlock(),
                        request.style(),
                        request.center(),
                        requireSurfaceData
                ))
                .filter(frame -> !request.layers().contains(
                        cartographer.render.RenderLayer.MARKERS
                ) || frame.decorationState().orElseThrow().userMarkersAvailable());
        setBusy(true);
        if (reusable.isPresent()) {
            MapFrame frame = reusable.orElseThrow();
            workstation.setStatus("Rendering map with retained base data...");
            operationCoordinator.submitProgress(
                    WorkstationOperationScope.FOREGROUND,
                    "map-retained-render",
                    "Map retained R" + request.radius(),
                    progress -> useCase.executeRetained(
                            request,
                            frame.savePath(),
                            frame.preparedMapData().orElseThrow(),
                            frame.decorationState().orElseThrow(),
                            frame.mapRegionOverlayState(),
                            progress
                    ),
                    result -> showMapResult(result, request),
                    this::showFailure
            );
            return;
        }

        workstation.setStatus("Rendering map...");
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "map-render",
                "Map R" + request.radius(),
                progress -> useCase.execute(request, progress),
                result -> showMapResult(result, request),
                this::showFailure
        );
    }

    private void renderRockMap() {
        RenderRockMapRequest request = rockRequestFromControls();
        setBusy(true);
        workstation.setStatus("Rendering observed rock geology...");
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "rock-render",
                "Geology R" + request.radius(),
                progress -> rockUseCase.execute(request, progress),
                result -> showRockResult(result, request),
                this::showFailure
        );
    }

    private void analyzeProspectingArea() {
        if (worldPanel.savePathText().isBlank()) {
            showFailure(new IllegalArgumentException("Select a .vcdbs save."));
            return;
        }
        List<String> resources = searchPanel.prospectingResourceKeys();
        if (!searchPanel.prospectingAllResources() && resources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select at least one prospecting resource or choose All resources."
            );
        }
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of(worldPanel.savePathText()),
                Optional.empty(),
                searchPanel.selectedRadius(),
                searchPanel.prospectingAllResources() ? List.of() : resources
        );
        setBusy(true);
        workstation.setStatus("Analyzing prospecting evidence...");
        operationCoordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "prospecting-analysis",
                "Prospecting R" + request.radius()
                        + " (" + (request.allResources()
                        ? "all resources"
                        : request.resources().size() + " selected") + ")",
                () -> prospectingUseCase.execute(request),
                result -> showProspectingResult(result, request),
                this::showFailure
        );
    }

    private RenderRockMapRequest rockRequestFromControls() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        OptionalInt y = OptionalInt.empty();
        if (searchPanel.rockAtY()) {
            Integer value = parseOptionalInteger(searchPanel.rockYText(), "Rock Y");
            if (value == null) {
                throw new IllegalArgumentException("Enter a world Y for At Y mode.");
            }
            y = OptionalInt.of(value);
        }
        return new RenderRockMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.rockAtY() ? RockMapMode.AT_Y : RockMapMode.UPPER_ROCK,
                searchPanel.selectedRadius(),
                Optional.empty(),
                y,
                OptionalInt.empty(),
                OptionalInt.empty()
        );
    }

    private void renderSurfaceResource() {
        if (searchPanel.selectedSurfaceMode() == SurfaceToolMode.MATERIALS) {
            renderSurfaceMaterial();
            return;
        }
        List<ObservedSurfaceResource> selected = selectedSurfaceResourcesForRender();
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key = currentSurfaceDiscoveryKey();
        if (surfaceDiscoveryResult == null
                || !key.equals(surfaceDiscoveryTaskKey)
                || !surfaceDiscoveryResult.observedResources().resources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(selected.stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey()).toList())) {
            throw new IllegalStateException(
                    "Surface object discovery is not current; scan the save first."
            );
        }
        selected = selected.stream()
                .map(resource -> surfaceDiscoveryResult.observedResources()
                        .findByQualifiedResourceKey(resource.candidate().qualifiedResourceKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "Selected surface object is not in the current discovery result: "
                                        + resource.candidate().qualifiedResourceKey())))
                .toList();
        RenderSurfaceResourceMapRequest request = RenderSurfaceResourceMapRequest.forObservedResources(
                key.savePath(),
                key.radius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                selected,
                surfaceDiscoveryResult.center()
        );
        submitSurfaceRender(request);
    }

    private void renderSurfaceMaterial() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        SurfaceMaterialMatch match = searchPanel.surfaceMaterialMatch().orElseThrow(
                () -> new IllegalStateException("Select a surface material."));
        RenderSurfaceResourceMapRequest request = new RenderSurfaceResourceMapRequest(
                Path.of(worldPanel.savePathText()), searchPanel.selectedRadius(), 1,
                RenderStyle.TOPOGRAPHIC, workstation.selectedRenderLayers(), match, Optional.empty());
        submitSurfaceRender(request);
    }

    private void submitSurfaceRender(RenderSurfaceResourceMapRequest request) {
        Optional<MapFrame> reusable = mapFrameState.current()
                .filter(frame -> frame.canReusePreparedMap(
                        request.savePath(),
                        request.radius(),
                        request.pixelsPerBlock(),
                        request.style(),
                        request.center(),
                        true
                ))
                .filter(frame -> frame.supportsLocalRecomposition(request.layers()));
        setBusy(true);
        if (reusable.isPresent()) {
            MapFrame frame = reusable.orElseThrow();
            workstation.setStatus("Rendering Surface from retained map data...");
            operationCoordinator.submitProgress(
                    WorkstationOperationScope.FOREGROUND,
                    "surface-retained-render",
                    "Surface retained R" + request.radius(),
                    progress -> surfaceUseCase.executeRetained(
                            request,
                            frame.savePath(),
                            frame.preparedMapData().orElseThrow(),
                            frame.decorationState().orElseThrow(),
                            progress
                    ),
                    result -> showSurfaceResult(result, request),
                    this::showFailure
            );
            return;
        }

        workstation.setStatus("Rendering surface resource...");
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "surface-render",
                "Surface R" + request.radius(),
                progress -> surfaceUseCase.execute(request, progress),
                result -> showSurfaceResult(result, request),
                this::showFailure
        );
    }

    private RenderActualOreMapRequest requestFromControls() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        List<ActualOreOverlaySpec> overlays = selectedOverlays();
        String match = overlays.isEmpty() ? "" : overlays.getFirst().match();
        if (match.isBlank()) {
            throw new IllegalArgumentException(
                            searchPanel.multipleResources()
                            ? "Select at least one resource."
                            : "Enter an ore match."
            );
        }
        Integer min = null;
        Integer max = null;
        if (searchPanel.customYEnabled()) {
            min = parseOptionalInteger(searchPanel.yMinText(), "Y minimum");
            max = parseOptionalInteger(searchPanel.yMaxText(), "Y maximum");
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("Y minimum must not exceed Y maximum.");
            }
        }
        return new RenderActualOreMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                Optional.of(match),
                new ActualBlockYFilter(min, max),
                Optional.empty(),
                overlays
        );
    }

    private RenderActualOreMapRequest mapRequestFromControls() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        return new RenderActualOreMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                Optional.empty(),
                ActualBlockYFilter.unbounded(),
                Optional.empty(),
                List.of()
        );
    }

    private void showResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        localRecompositionGate.invalidate();
        mapPanel.show(result.image(), Optional.of(result.geometry()), loadedPlayerAbsolute);
        mapFrameState.retain(MapFrame.ore(
                request.savePath(),
                result.geometry(),
                result.preparedMapData().orElseThrow(
                        () -> new IllegalStateException("ore result missing prepared map data")
                ),
                result.actualOreOverlays(),
                result.decorationState().orElseThrow(
                        () -> new IllegalStateException("ore result missing decoration state")
                ),
                result.mapRegionOverlayState().orElseThrow(
                        () -> new IllegalStateException("ore result missing map-region overlay state")
                )
        ));
        workstation.setMapGeometry(Optional.of(result.geometry()));
        resultInspector.showOreResult(result, request);
        workstation.setStatus("Rendered.");
        setBusy(false);
    }

    private void showMapResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        localRecompositionGate.invalidate();
        mapPanel.show(result.image(), Optional.of(result.geometry()), loadedPlayerAbsolute);
        mapFrameState.retain(MapFrame.map(
                request.savePath(),
                result.geometry(),
                result.preparedMapData().orElseThrow(
                        () -> new IllegalStateException("map result missing prepared map data")
                ),
                result.decorationState().orElseThrow(
                        () -> new IllegalStateException("map result missing decoration state")
                ),
                result.mapRegionOverlayState().orElseThrow(
                        () -> new IllegalStateException("map result missing map-region overlay state")
                )
        ));
        workstation.setMapGeometry(Optional.of(result.geometry()));
        resultInspector.showMapResult(result, request);
        workstation.setStatus("Map rendered.");
        setBusy(false);
    }

    private void showCoverageResult(
            RenderCoverageMapResult result,
            RenderCoverageMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(result.image(), result.geometry(), loadedPlayerAbsolute);
        mapFrameState.clear();
        result.geometry().ifPresent(geometry ->
                mapFrameState.retain(MapFrame.coverage(request.savePath(), geometry)));
        workstation.setMapGeometry(result.geometry());
        resultInspector.showCoverageResult(result);
        workstation.setStatus("Coverage rendered.");
        setBusy(false);
    }

    private void showSurfaceResult(
            RenderSurfaceResourceMapResult result,
            RenderSurfaceResourceMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(result.image(), Optional.of(result.geometry()), loadedPlayerAbsolute);
        mapFrameState.retain(MapFrame.surface(
                request.savePath(),
                result.geometry(),
                result.preparedMapData().orElseThrow(
                        () -> new IllegalStateException("surface result missing prepared map data")
                ),
                result.analysis(),
                result.decorationState().orElseThrow(
                        () -> new IllegalStateException("surface result missing decoration state")
                )
        ));
        workstation.setMapGeometry(Optional.of(result.geometry()));
        resultInspector.showSurfaceResult(result, request);
        workstation.setStatus("Rendered.");
        setBusy(false);
    }

    private List<ObservedSurfaceResource> selectedSurfaceResourcesForRender() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        List<ObservedSurfaceResource> selected = workstation.selectedObservedSurfaceResources();
        if (selected.isEmpty()) {
            throw new IllegalStateException(
                    "No observed surface object is available in this radius."
            );
        }
        return selected;
    }

    private void handleModeChanged(WorkstationTool mode) {
        if (mode == WorkstationTool.GEOLOGY
                || mode == WorkstationTool.PROSPECTING) {
            List<cartographer.geology.rock.RockIdentity> rocks =
                    mapFrameState.current()
                            .flatMap(MapFrame::rockMap)
                            .map(cartographer.geology.rock.RockMap::ordinalTable)
                            .orElseGet(List::of);
            searchPanel.setRockLegend(rocks);
        }
        maybeStartSurfaceObjectDiscovery();
    }

    private void handleSurfaceModeChanged(SurfaceToolMode mode) {
        maybeStartSurfaceObjectDiscovery();
    }

    private void handleRenderLayersChanged(Set<cartographer.render.RenderLayer> layers) {
        Optional<MapFrame> current = mapFrameState.current();
        if (current.isEmpty()) {
            return;
        }
        MapFrame frame = current.orElseThrow();
        if (!frame.supportsLocalRecomposition(layers)) {
            if (frame.tool() == WorkstationTool.MAP
                    || frame.tool() == WorkstationTool.ORE
                    || frame.tool() == WorkstationTool.SURFACE) {
                workstation.setStatus(
                        "Selected layers need data not retained in this frame; press Render."
                );
            }
            return;
        }

        if (worldPanel.savePathText().isBlank()
                || !Path.of(worldPanel.savePathText())
                .toAbsolutePath()
                .normalize()
                .equals(frame.savePath())) {
            return;
        }

        LocalRecompositionGate.Token token =
                localRecompositionGate.begin(frame, layers);
        workstation.setLocalBusy(true);
        if (!operationCoordinator.isActive(WorkstationOperationScope.FOREGROUND)) {
            workstation.setStatus("Recomposing layers locally...");
        }
        operationCoordinator.submitProgress(
                WorkstationOperationScope.LOCAL,
                "layer-recomposition",
                "Layers " + layers,
                progress -> mapFrameCompositor.recompose(frame, layers, progress),
                image -> {
                    workstation.setLocalBusy(false);
                    if (!localRecompositionGate.accepts(
                            token,
                            mapFrameState.current(),
                            workstation.selectedRenderLayers()
                    )) {
                        return;
                    }
                    mapPanel.replaceImage(
                            image,
                            Optional.of(frame.geometry()),
                            loadedPlayerAbsolute
                    );
                    if (!operationCoordinator.isActive(
                            WorkstationOperationScope.FOREGROUND
                    )) {
                        workstation.setStatus(
                                "Layers recomposed locally (no save read)."
                        );
                    }
                },
                failure -> {
                    workstation.setLocalBusy(false);
                    if (localRecompositionGate.accepts(
                            token,
                            mapFrameState.current(),
                            workstation.selectedRenderLayers()
                    ) && !operationCoordinator.isActive(
                            WorkstationOperationScope.FOREGROUND
                    )) {
                        workstation.setStatus(
                                "Local recomposition failed: "
                                        + conciseMessage(failure)
                        );
                    }
                }
        );
    }

    private void handleRadiusChanged(int radius) {
        maybeStartSurfaceObjectDiscovery();
    }

    private void maybeStartSurfaceObjectDiscovery() {
        if (searchPanel.selectedMode() != WorkstationTool.SURFACE
                || searchPanel.selectedSurfaceMode()
                != SurfaceToolMode.OBJECTS
                || worldPanel.savePathText().isBlank()) {
            return;
        }

        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentKey =
                currentSurfaceDiscoveryKey();
        boolean currentTaskMatches = surfaceDiscoveryTaskKey != null
                && surfaceDiscoveryTaskKey.equals(currentKey);
        if (surfaceObjectDiscoveryState.isCurrentFor(currentTaskMatches)) {
            return;
        }

        startSurfaceDiscovery(currentKey.savePath());
    }

    private SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentSurfaceDiscoveryKey() {
        return new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius()
        );
    }

    private void invalidateSurfaceDiscovery() {
        surfaceDiscoveryGate.invalidate();
        operationCoordinator.cancel(WorkstationOperationScope.DISCOVERY);
        surfaceDiscoveryCache.clear();
        surfaceDiscoveryCenter = Optional.empty();
        surfaceDiscoveryResult = null;
        surfaceDiscoveryTaskKey = null;
        workstation.clearObservedSurfaceResources();
        surfaceObjectDiscoveryState = SurfaceObjectDiscoveryState.NOT_SCANNED;
        workstation.setSurfaceObjectDiscoveryState(surfaceObjectDiscoveryState);
    }

    private void startSurfaceDiscovery(Path savePath) {
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key = new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(
                savePath,
                searchPanel.selectedRadius()
        );

        if (surfaceDiscoveryCenter.isPresent()) {
            SurfaceDiscoveryCacheKey cacheKey = SurfaceDiscoveryCacheKey.of(
                    key.savePath(), key.radius(), surfaceDiscoveryCenter.orElseThrow());
            Optional<DiscoverObservedSurfaceResourcesResult> cached = surfaceDiscoveryCache.get(cacheKey);
            if (SurfaceDiscoveryPolicy.activation(cached.isPresent(), false)
                    == SurfaceDiscoveryPolicy.Activation.CACHE_HIT) {
                surfaceDiscoveryGate.begin(key);
                surfaceSelectionKeys = workstation.selectedObservedSurfaceResources().stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                workstation.setDiscoveryBusy(false);
                applySurfaceDiscoveryResult(key, cached.orElseThrow());
                return;
            }
        }
        boolean sameKeyScanInFlight = surfaceDiscoveryTask != null
                && !surfaceDiscoveryTask.isDone()
                && key.equals(surfaceDiscoveryTaskKey);
        if (SurfaceDiscoveryPolicy.activation(false, sameKeyScanInFlight)
                == SurfaceDiscoveryPolicy.Activation.ALREADY_SCANNING) {
            return;
        }
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryToken token = surfaceDiscoveryGate.begin(key);
        surfaceDiscoveryResult = null;
        surfaceDiscoveryTaskKey = key;
        surfaceSelectionKeys = workstation.selectedObservedSurfaceResources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        workstation.clearObservedSurfaceResources();
        surfaceObjectDiscoveryState = SurfaceObjectDiscoveryState.SCANNING;
        workstation.setSurfaceObjectDiscoveryState(surfaceObjectDiscoveryState);
        workstation.setDiscoveryBusy(true);
        surfaceDiscoveryTask = operationCoordinator.submitProgress(
                WorkstationOperationScope.DISCOVERY,
                "surface-object-discovery",
                "Surface discovery R" + key.radius(),
                progress -> surfaceDiscoveryUseCase.execute(
                        new DiscoverObservedSurfaceResourcesRequest(
                                key.savePath(),
                                key.radius(),
                                surfaceDiscoveryCenter
                        ),
                        progress
                ),
                result -> {
                    workstation.setDiscoveryBusy(false);
                    if (!SurfaceDiscoveryPolicy.shouldCacheCompletion(
                            surfaceDiscoveryGate.accepts(token, currentSurfaceDiscoveryKey()))) {
                        return;
                    }
                    if (surfaceDiscoveryCenter.isEmpty()) {
                        surfaceDiscoveryCenter = Optional.of(result.center());
                    }
                    surfaceDiscoveryCache.put(
                            SurfaceDiscoveryCacheKey.of(
                                    key.savePath(),
                                    key.radius(),
                                    result.center()
                            ),
                            result
                    );
                    applySurfaceDiscoveryResult(key, result);
                },
                failure -> {
                    workstation.setDiscoveryBusy(false);
                    if (!surfaceDiscoveryGate.accepts(token, currentSurfaceDiscoveryKey())) {
                        return;
                    }
                    showSurfaceDiscoveryFailure();
                }
        );
    }

    private void applySurfaceDiscoveryResult(
            SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key,
            DiscoverObservedSurfaceResourcesResult result
    ) {
        surfaceDiscoveryTaskKey = key;
        surfaceDiscoveryResult = result;
        workstation.setObservedSurfaceResources(result.observedResources(), surfaceSelectionKeys);
        surfaceSelectionKeys = workstation.selectedObservedSurfaceResources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        surfaceObjectDiscoveryState = result.observedResources().resources().isEmpty()
                ? SurfaceObjectDiscoveryState.EMPTY
                : SurfaceObjectDiscoveryState.READY;
        workstation.setSurfaceObjectDiscoveryState(surfaceObjectDiscoveryState);
    }

    private void showRockResult(
            RenderRockMapResult result,
            RenderRockMapRequest request
    ) {
        localRecompositionGate.invalidate();
        rockHighlightGeneration++;
        var rockMap = result.retainedMap().orElseThrow(() ->
                new IllegalStateException(
                        "Geology Workstation render requires retained ROCK data"
                )
        );
        searchPanel.setRockLegend(rockMap.ordinalTable());
        var displayed = searchPanel.selectedRockHighlight().isPresent()
                ? rockUseCase.renderRetained(
                rockMap,
                searchPanel.selectedRockHighlight()
        )
                : result.rendered();
        mapPanel.show(
                displayed.image(),
                Optional.of(displayed.geometry()),
                loadedPlayerAbsolute
        );
        mapFrameState.retain(MapFrame.geology(
                request.savePath(),
                displayed.geometry(),
                rockMap
        ));
        workstation.setMapGeometry(Optional.of(displayed.geometry()));
        resultInspector.showRockResult(result, request);
        workstation.setStatus("Rock map rendered.");
        setBusy(false);
    }

    private void showProspectingResult(
            ProspectingAreaResult result,
            ProspectingAreaRequest request
    ) {
        localRecompositionGate.invalidate();
        rockHighlightGeneration++;
        result.rockMap().ifPresent(rockMap -> {
            searchPanel.setRockLegend(rockMap.ordinalTable());
            var rendered = rockUseCase.renderRetained(
                    rockMap,
                    searchPanel.selectedRockHighlight()
            );
            mapPanel.show(
                    rendered.image(),
                    Optional.of(rendered.geometry()),
                    loadedPlayerAbsolute
            );
            mapFrameState.retain(MapFrame.prospecting(
                    request.savePath(),
                    rendered.geometry(),
                    rockMap
            ));
            workstation.setMapGeometry(Optional.of(rendered.geometry()));
        });
        resultInspector.showProspectingResult(result, request);
        workstation.setStatus("Prospecting analysis complete.");
        setBusy(false);
    }

    private PlayerPositionSnapshot playerSnapshot(
            WorldMetadata metadata,
            WorldPosition absolute
    ) {
        var display = metadata.toDisplay(absolute);
        var chunk = absolute.chunkCoordinate();
        return new PlayerPositionSnapshot(
                absolute,
                new PlayerPositionView(
                        display.x(),
                        display.y(),
                        display.z(),
                        chunk.x(),
                        chunk.z()
                )
        );
    }

    private String formatPlayer(PlayerPositionView player) {
        return String.format(
                java.util.Locale.ROOT,
                "X: %.1f   Z: %.1f%nY: %.1f%nChunk: %d, %d",
                player.x(),
                player.z(),
                player.y(),
                player.chunkX(),
                player.chunkZ()
        );
    }

    private void handleCursorPositionChanged(Optional<MapCursorPosition> cursor) {
        if (cursor.isEmpty() || loadedWorldMetadata.isEmpty()) {
            workstation.clearCursorCoordinates();
            resultInspector.clearCursorInspection();
            return;
        }
        MapCursorPosition absolute = cursor.orElseThrow();
        var display = loadedWorldMetadata.orElseThrow().toDisplay(
                new cartographer.model.WorldPosition(
                        absolute.absoluteX(),
                        0.0,
                        absolute.absoluteZ()
                )
        );
        workstation.setCursorCoordinates(display.x(), display.z());

        Optional<cartographer.geology.rock.RockColumnSample> rockSample =
                mapFrameState.current()
                        .flatMap(MapFrame::rockMap)
                        .flatMap(rockMap -> rockMap.sampleAt(
                                floorWorldCoordinate(absolute.absoluteX()),
                                floorWorldCoordinate(absolute.absoluteZ())
                        ));
        resultInspector.showRockCursor(rockSample);
    }

    private int floorWorldCoordinate(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world coordinate is outside supported block range"
            );
        }
        return (int) floored;
    }

    private void handleRockHighlightChanged(Optional<String> rockCode) {
        Optional<MapFrame> current = mapFrameState.current();
        if (current.isEmpty() || current.orElseThrow().rockMap().isEmpty()) {
            return;
        }
        MapFrame frame = current.orElseThrow();
        if (frame.tool() != WorkstationTool.GEOLOGY
                && frame.tool() != WorkstationTool.PROSPECTING) {
            return;
        }
        long generation = ++rockHighlightGeneration;
        workstation.setLocalBusy(true);
        if (!operationCoordinator.isActive(WorkstationOperationScope.FOREGROUND)) {
            workstation.setStatus("Highlighting rock locally...");
        }
        operationCoordinator.submit(
                WorkstationOperationScope.LOCAL,
                "rock-highlight",
                rockCode.map(code -> "Highlight " + code)
                        .orElse("Clear rock highlight"),
                () -> rockUseCase.renderRetained(
                        frame.rockMap().orElseThrow(),
                        rockCode
                ),
                rendered -> {
                    workstation.setLocalBusy(false);
                    if (generation != rockHighlightGeneration
                            || mapFrameState.current().filter(frame::equals).isEmpty()
                            || !searchPanel.selectedRockHighlight().equals(rockCode)) {
                        return;
                    }
                    mapPanel.replaceImage(
                            rendered.image(),
                            Optional.of(rendered.geometry()),
                            loadedPlayerAbsolute
                    );
                    workstation.setMapGeometry(Optional.of(rendered.geometry()));
                    if (!operationCoordinator.isActive(
                            WorkstationOperationScope.FOREGROUND
                    )) {
                        workstation.setStatus(
                                rockCode.map(
                                                code -> "Rock highlighted locally: " + code
                                        )
                                        .orElse("Rock highlight cleared locally.")
                        );
                    }
                },
                failure -> {
                    workstation.setLocalBusy(false);
                    if (generation == rockHighlightGeneration
                            && !operationCoordinator.isActive(
                            WorkstationOperationScope.FOREGROUND
                    )) {
                        workstation.setStatus(
                                "Local rock highlight failed: "
                                        + conciseMessage(failure)
                        );
                    }
                }
        );
    }

    private void cancelPreferredOperation() {
        if (operationCoordinator.cancelPreferred()) {
            workstation.setStatus("Cancelling operation...");
        }
    }

    private void handleOperationCancelled(WorkstationOperationScope scope) {
        switch (scope) {
            case FOREGROUND -> {
                setBusy(false);
                workstation.setSnapshotPreparing(false);
                if (!worldPanel.savePathText().isBlank()) {
                    refreshSnapshotStatus(
                            Path.of(worldPanel.savePathText())
                    );
                }
            }
            case DISCOVERY -> {
                workstation.setDiscoveryBusy(false);
                if (surfaceObjectDiscoveryState == SurfaceObjectDiscoveryState.SCANNING) {
                    surfaceDiscoveryResult = null;
                    surfaceDiscoveryTaskKey = null;
                    workstation.clearObservedSurfaceResources();
                    surfaceObjectDiscoveryState =
                            SurfaceObjectDiscoveryState.NOT_SCANNED;
                    workstation.setSurfaceObjectDiscoveryState(
                            surfaceObjectDiscoveryState
                    );
                }
            }
            case LOCAL -> {
                localRecompositionGate.invalidate();
                rockHighlightGeneration++;
                workstation.setLocalBusy(false);
            }
        }
        if (!operationCoordinator.isActive(WorkstationOperationScope.FOREGROUND)
                && !operationCoordinator.isActive(WorkstationOperationScope.LOCAL)
                && !operationCoordinator.isActive(WorkstationOperationScope.DISCOVERY)) {
            workstation.setStatus("Cancelled.");
        }
    }

    private void showFailure(Throwable failure) {
        workstation.setStatus("Error: " + conciseMessage(failure));
        resultInspector.showError(failure);
        setBusy(false);
    }

    private String conciseMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void setBusy(boolean busy) {
        if (busy
                && operationCoordinator.isActive(
                WorkstationOperationScope.DISCOVERY
        )) {
            operationCoordinator.cancel(WorkstationOperationScope.DISCOVERY);
        }
        workstation.setBusy(busy);
    }

    private void showSurfaceDiscoveryFailure() {
        surfaceDiscoveryResult = null;
        surfaceObjectDiscoveryState = SurfaceObjectDiscoveryState.FAILED;
        workstation.clearObservedSurfaceResources();
        workstation.setSurfaceObjectDiscoveryState(surfaceObjectDiscoveryState);
    }

    private List<ActualOreOverlaySpec> selectedOverlays() {
        return searchPanel.selectedOverlays();
    }

    private Integer parseOptionalInteger(String text, String label) {
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
    }

}
