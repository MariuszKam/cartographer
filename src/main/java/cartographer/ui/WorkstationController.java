package cartographer.ui;

import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.DiscoverObservedSurfaceResourcesRequest;
import cartographer.application.DiscoverObservedSurfaceResourcesResult;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.LoadWorldOverviewUseCase;
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
import cartographer.ui.workstation.MapFrame;
import cartographer.ui.workstation.MapFrameCompositor;
import cartographer.ui.workstation.MapFrameState;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.ResultInspectorPane;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.SurfaceObjectDiscoveryState;
import cartographer.ui.workstation.SurfaceToolMode;
import cartographer.ui.workstation.WorkstationOperationCoordinator;
import cartographer.ui.workstation.WorkstationTool;
import cartographer.ui.workstation.WorkstationView;
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

    private final RenderActualOreMapUseCase useCase;
    private final RenderCoverageMapUseCase coverageUseCase;
    private final RenderSurfaceResourceMapUseCase surfaceUseCase;
    private final DiscoverObservedSurfaceResourcesUseCase surfaceDiscoveryUseCase;
    private final RenderRockMapUseCase rockUseCase;
    private final AnalyzeProspectingAreaUseCase prospectingUseCase;
    private final LoadWorldOverviewUseCase worldOverviewUseCase;
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
            LoadWorldOverviewUseCase worldOverviewUseCase
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

        workstation = new WorkstationView(this::chooseSave, this::render);
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
    }

    public Parent root() {
        return workstation.root();
    }

    private void chooseSave() {
        saveChooser.get().ifPresent(savePath -> {
            worldPanel.setSavePath(savePath.toString());
            workstation.setSavePath(savePath);
            workstation.setStatus("");
            loadSaveData(savePath);
        });
    }

    private void loadSaveData(Path savePath) {
        loadedPlayerAbsolute = Optional.empty();
        loadedWorldMetadata = Optional.empty();
        mapPanel.clearNavigationContext();
        mapFrameState.clear();
        workstation.clearMapGeometry();
        surfaceSelectionKeys = Set.of();
        invalidateSurfaceDiscovery();
        workstation.setDiscoveryBusy(true);
        workstation.setStatus("Loading resources and player position...");
        workstation.setPlayerLoaded(false);
        worldPanel.setPlayerStatus("Player: loading...");

        operationCoordinator.submit(
                "cartographer-resource-discovery",
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
                    workstation.setDiscoveryBusy(false);
                    startSurfaceDiscovery(savePath);
                },
                failure -> {
                    searchPanel.setDiscoveryFailure();
                    worldPanel.setPlayerStatus("Player: unavailable");
                    workstation.setPlayerLoaded(false);
                    showFailure(failure);
                    workstation.setDiscoveryBusy(false);
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
            setBusy(true);
            workstation.setStatus("Rendering ore map...");
            operationCoordinator.submitProgress(
                    "cartographer-ore-map-render",
                    progress -> useCase.execute(request, progress),
                    result -> showResult(result, request),
                    this::showFailure
            );
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
                "cartographer-coverage-render",
                progress -> coverageUseCase.execute(request, progress),
                result -> showCoverageResult(result, request),
                this::showFailure
        );
    }

    private void renderMap() {
        RenderActualOreMapRequest request = mapRequestFromControls();
        setBusy(true);
        workstation.setStatus("Rendering map...");
        operationCoordinator.submitProgress(
                "cartographer-map-render",
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
                "cartographer-rock-map-render",
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
        String resource = searchPanel.prospectingResourceText();
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of(worldPanel.savePathText()),
                Optional.empty(),
                searchPanel.selectedRadius(),
                resource.isBlank() ? Optional.empty() : Optional.of(resource)
        );
        setBusy(true);
        workstation.setStatus("Analyzing prospecting evidence...");
        operationCoordinator.submit(
                "cartographer-prospecting-analysis",
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
        setBusy(true);
        workstation.setStatus("Rendering surface resource...");
        operationCoordinator.submitProgress(
                "cartographer-surface-resource-render",
                progress -> surfaceUseCase.execute(request, progress),
                result -> showSurfaceResult(result, request),
                this::showFailure
        );
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
        setBusy(true);
        workstation.setStatus("Rendering surface resource...");
        operationCoordinator.submitProgress(
                "cartographer-surface-material-render",
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
        if (mode == WorkstationTool.SURFACE
                && searchPanel.selectedSurfaceMode() == SurfaceToolMode.OBJECTS
                && !worldPanel.savePathText().isBlank()) {
            SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentKey = currentSurfaceDiscoveryKey();
            if (!surfaceObjectDiscoveryState.isCurrentFor(
                    surfaceDiscoveryTaskKey != null && surfaceDiscoveryTaskKey.equals(currentKey))) {
                startSurfaceDiscovery(currentKey.savePath());
            }
        }
    }

    private void handleSurfaceModeChanged(SurfaceToolMode mode) {
        if (mode == SurfaceToolMode.OBJECTS
                && searchPanel.selectedMode() == WorkstationTool.SURFACE
                && !worldPanel.savePathText().isBlank()
                && !surfaceObjectDiscoveryState.isCurrentFor(
                        surfaceDiscoveryTaskKey != null
                                && surfaceDiscoveryTaskKey.equals(currentSurfaceDiscoveryKey()))) {
            startSurfaceDiscovery(Path.of(worldPanel.savePathText()));
        }
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

        setBusy(true);
        workstation.setStatus("Recomposing layers locally...");
        operationCoordinator.submitProgress(
                "cartographer-local-layer-recompose",
                progress -> mapFrameCompositor.recompose(frame, layers, progress),
                image -> {
                    if (mapFrameState.current().filter(frame::equals).isEmpty()) {
                        setBusy(false);
                        return;
                    }
                    mapPanel.replaceImage(
                            image,
                            Optional.of(frame.geometry()),
                            loadedPlayerAbsolute
                    );
                    workstation.setStatus(
                            "Layers recomposed locally (no save read)."
                    );
                    setBusy(false);
                },
                this::showFailure
        );
    }

    private void handleRadiusChanged(int radius) {
        if (!worldPanel.savePathText().isBlank()) {
            startSurfaceDiscovery(Path.of(worldPanel.savePathText()));
        }
    }

    private SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentSurfaceDiscoveryKey() {
        return new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius()
        );
    }

    private void invalidateSurfaceDiscovery() {
        surfaceDiscoveryGate.invalidate();
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
        surfaceDiscoveryTask = operationCoordinator.submit(
                "cartographer-surface-object-discovery",
                () -> surfaceDiscoveryUseCase.execute(
                        new DiscoverObservedSurfaceResourcesRequest(
                                key.savePath(),
                                key.radius(),
                                surfaceDiscoveryCenter
                        )
                ),
                result -> {
                    if (!SurfaceDiscoveryPolicy.shouldCacheCompletion(
                            surfaceDiscoveryGate.accepts(token, currentSurfaceDiscoveryKey()))) {
                        return;
                    }
                    if (surfaceDiscoveryCenter.isEmpty()) {
                        surfaceDiscoveryCenter = Optional.of(result.center());
                    }
                    surfaceDiscoveryCache.put(
                            SurfaceDiscoveryCacheKey.of(key.savePath(), key.radius(), result.center()),
                            result
                    );
                    applySurfaceDiscoveryResult(key, result);
                },
                failure -> {
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
        mapPanel.show(
                result.rendered().image(),
                Optional.of(result.rendered().geometry()),
                loadedPlayerAbsolute
        );
        mapFrameState.retain(MapFrame.geology(
                request.savePath(),
                result.rendered().geometry(),
                result.map()
        ));
        workstation.setMapGeometry(Optional.of(result.rendered().geometry()));
        resultInspector.showRockResult(result, request);
        workstation.setStatus("Rock map rendered.");
        setBusy(false);
    }

    private void showProspectingResult(ProspectingAreaResult result, ProspectingAreaRequest request) {
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
            return;
        }
        MapCursorPosition absolute = cursor.orElseThrow();
        var display = loadedWorldMetadata.orElseThrow().toDisplay(
                new cartographer.model.WorldPosition(absolute.absoluteX(), 0.0, absolute.absoluteZ())
        );
        workstation.setCursorCoordinates(display.x(), display.z());
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
