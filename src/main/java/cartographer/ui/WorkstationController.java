package cartographer.ui;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.LoadWorldOverviewUseCase;
import cartographer.application.InspectWorldSnapshotStatusUseCase;
import cartographer.application.PrepareWorldSnapshotRequest;
import cartographer.application.PrepareWorldSnapshotResult;
import cartographer.application.PrepareWorldSnapshotUseCase;
import cartographer.application.ProgressiveMapEvent;
import cartographer.application.ProgressiveMapSession;
import cartographer.application.ProgressiveMapSessionFactory;
import cartographer.application.WorldSnapshotStatus;
import cartographer.application.ProspectingAreaRequest;
import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.RenderCoverageMapRequest;
import cartographer.application.RenderCoverageMapUseCase;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapUseCase;
import cartographer.application.RenderSurfaceResourceMapUseCase;
import cartographer.geology.rock.RockMapMode;
import cartographer.marker.MarkerStore;
import cartographer.model.WorldMetadata;
import cartographer.navigation.HomeStore;
import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.RenderTileCoordinate;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.ui.workstation.MapFrame;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.ResultInspectorPane;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.WorkstationOperationCoordinator;
import cartographer.ui.workstation.WorkstationOperationScope;
import cartographer.ui.workstation.WorkstationTool;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.workstation.WorldMapViewport;
import cartographer.ui.workstation.WorldMapViewportRequest;
import cartographer.ui.workstation.WorldMapMarker;
import cartographer.ui.update.UpdateCheckView;
import cartographer.ui.workstation.WorldPanel;
import javafx.application.Platform;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * JavaFX Workstation orchestration layer.
 *
 * <p>The desktop application owns dependency construction and Stage/FileChooser setup.
 * This controller owns Workstation state, request assembly, background-operation
 * orchestration and result presentation.</p>
 */
public final class WorkstationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkstationController.class);

    private final Supplier<Optional<Path>> saveChooser;
    private final WorkstationView workstation;
    private final SearchPanel searchPanel;
    private final WorldPanel worldPanel;
    private final MapPanel mapPanel;
    private final WorldMapViewport progressiveMapViewport;
    private final ResultInspectorPane resultInspector;
    private final WorkstationOperationCoordinator operationCoordinator;
    private final WorkstationMapFrameController mapFrameController;
    private final SurfaceToolController surfaceToolController;

    private final RenderActualOreMapUseCase useCase;
    private final RenderCoverageMapUseCase coverageUseCase;
    private final RenderRockMapUseCase rockUseCase;
    private final AnalyzeProspectingAreaUseCase prospectingUseCase;
    private final LoadWorldOverviewUseCase worldOverviewUseCase;
    private final PrepareWorldSnapshotUseCase prepareWorldSnapshotUseCase;
    private final InspectWorldSnapshotStatusUseCase snapshotStatusUseCase;
    private final ProgressiveMapSessionFactory progressiveMapSessionFactory;
    private final HomeStore homeStore;
    private final MarkerStore markerStore;
    private final OreResourceResolver resourceResolver = new OreResourceResolver();

    private Optional<cartographer.model.WorldPosition> loadedPlayerAbsolute = Optional.empty();
    private Optional<WorldMetadata> loadedWorldMetadata = Optional.empty();
    private Optional<cartographer.application.WorldOverview> loadedWorldOverview = Optional.empty();
    private ProgressiveMapSession progressiveMapSession;
    private final AtomicInteger progressiveTilesReady = new AtomicInteger();

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
            InspectWorldSnapshotStatusUseCase snapshotStatusUseCase,
            ProgressiveMapSessionFactory progressiveMapSessionFactory,
            HomeStore homeStore,
            MarkerStore markerStore
    ) {
        this.saveChooser = Objects.requireNonNull(saveChooser, "save chooser is required");
        this.useCase = Objects.requireNonNull(useCase, "map/ore use case is required");
        this.coverageUseCase = Objects.requireNonNull(coverageUseCase, "coverage use case is required");
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
        this.progressiveMapSessionFactory = Objects.requireNonNull(
                progressiveMapSessionFactory,
                "progressiveMapSessionFactory is required"
        );
        this.homeStore = Objects.requireNonNull(
                homeStore,
                "homeStore is required"
        );
        this.markerStore = Objects.requireNonNull(
                markerStore,
                "markerStore is required"
        );

        workstation = new WorkstationView(
                this::chooseSave,
                this::render,
                this::prepareWorldSnapshot,
                progressiveMapSessionFactory.layout()
        );
        worldPanel = workstation.worldPanel();
        searchPanel = workstation.searchPanel();
        mapPanel = workstation.mapPanel();
        progressiveMapViewport = workstation.progressiveMapViewport();
        resultInspector = workstation.resultInspectorPane();
        operationCoordinator = new WorkstationOperationCoordinator(workstation);
        mapFrameController = new WorkstationMapFrameController(
                workstation,
                searchPanel,
                worldPanel,
                mapPanel,
                resultInspector,
                operationCoordinator,
                rockUseCase,
                () -> loadedPlayerAbsolute,
                () -> loadedWorldMetadata
        );
        surfaceToolController = new SurfaceToolController(
                workstation,
                searchPanel,
                worldPanel,
                operationCoordinator,
                surfaceUseCase,
                surfaceDiscoveryUseCase,
                mapFrameController,
                this::showFailure
        );

        mapPanel.setOnCursorPositionChanged(
                mapFrameController::handleCursorPositionChanged
        );
        progressiveMapViewport.setOnCursorPositionChanged(
                mapFrameController::handleCursorPositionChanged
        );
        progressiveMapViewport.setOnTileDemand(
                this::handleProgressiveTileDemand
        );
        workstation.setOnModeChanged(this::handleModeChanged);
        workstation.setOnRadiusChanged(radius -> surfaceToolController.maybeStartDiscovery());
        workstation.setOnSurfaceModeChanged(mode -> surfaceToolController.maybeStartDiscovery());
        workstation.setOnRenderLayersChanged(
                this::handleRenderLayersChanged
        );
        searchPanel.setOnRockHighlightChanged(mapFrameController::handleRockHighlightChanged);
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
        stopProgressiveMap();
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
            LOGGER.warn("Snapshot status refresh failed for {}", savePath, failure);
            workstation.setSnapshotPreparing(false);
            workstation.setStatus(
                    "Snapshot status unavailable: "
                            + conciseMessage(failure)
            );
        }
    }

    private void loadSaveData(Path savePath) {
        stopProgressiveMap();
        progressiveMapViewport.clearTiles();
        workstation.setProgressiveMapAvailable(false);
        operationCoordinator.cancelAll();
        loadedPlayerAbsolute = Optional.empty();
        loadedWorldMetadata = Optional.empty();
        loadedWorldOverview = Optional.empty();
        workstation.setSnapshotPreparing(false);
        refreshSnapshotStatus(savePath);
        mapPanel.clearNavigationContext();
        mapFrameController.reset();
        surfaceToolController.invalidateDiscovery();
        setBusy(true);
        workstation.setStatus("Loading resources and player position...");
        workstation.setPlayerLoaded(false);
        worldPanel.setPlayerStatus("Player: loading...");

        operationCoordinator.submit(
                WorkstationOperationScope.FOREGROUND,
                "world-overview",
                () -> worldOverviewUseCase.execute(savePath),
                loaded -> {
                    List<OreResource> discovered = resourceResolver.resolve(
                            loaded.resourceKeys(),
                            loaded.blockRegistry()
                    );
                    searchPanel.setResources(discovered);
                    loadedPlayerAbsolute = loaded.playerAbsolute();
                    loadedWorldMetadata = Optional.of(loaded.metadata());
                    loadedWorldOverview = Optional.of(loaded);
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
                    startProgressiveMap(savePath, loaded);
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
                surfaceToolController.render();
                return;
            }
            if (searchPanel.selectedMode() == WorkstationTool.MAP) {
                workstation.setStatus(
                        "Map streams automatically from the selected save."
                );
                return;
            }
            RenderActualOreMapRequest request = requestFromControls();
            boolean requireSurfaceData =
                    request.layers().contains(cartographer.render.RenderLayer.SURFACE)
                            || request.layers().contains(
                            cartographer.render.RenderLayer.SOIL_FERTILITY
                    );
            Optional<MapFrame> reusable = mapFrameController.current()
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
                        progress -> useCase.executeRetained(
                                request,
                                frame.savePath(),
                                frame.preparedMapData().orElseThrow(),
                                frame.decorationState().orElseThrow(),
                                frame.mapRegionOverlayState(),
                                progress
                        ),
                        result -> mapFrameController.showOreResult(result, request),
                        this::showFailure
                );
            } else {
                workstation.setStatus("Rendering ore map...");
                operationCoordinator.submitProgress(
                        WorkstationOperationScope.FOREGROUND,
                        "ore-render",
                        progress -> useCase.execute(request, progress),
                        result -> mapFrameController.showOreResult(result, request),
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
                progress -> coverageUseCase.execute(request, progress),
                result -> mapFrameController.showCoverageResult(result, request),
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
                progress -> rockUseCase.execute(request, progress),
                result -> mapFrameController.showRockResult(result, request),
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
                () -> prospectingUseCase.execute(request),
                result -> mapFrameController.showProspectingResult(result, request),
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

    private void handleModeChanged(WorkstationTool mode) {
        workstation.setProgressiveMapAvailable(
                progressiveMapSession != null
        );
        mapFrameController.handleModeChanged(mode);
        surfaceToolController.maybeStartDiscovery();
    }

    private void handleRenderLayersChanged(
            java.util.Set<RenderLayer> layers
    ) {
        if (searchPanel.selectedMode() != WorkstationTool.MAP) {
            mapFrameController.handleRenderLayersChanged(layers);
            return;
        }

        Optional<cartographer.application.WorldOverview> overview =
                loadedWorldOverview;
        if (overview.isEmpty() || worldPanel.savePathText().isBlank()) {
            return;
        }

        Path savePath = Path.of(worldPanel.savePathText())
                .toAbsolutePath()
                .normalize();
        startProgressiveMap(savePath, overview.orElseThrow());
    }

    private void startProgressiveMap(
            Path savePath,
            cartographer.application.WorldOverview overview
    ) {
        stopProgressiveMap();
        progressiveTilesReady.set(0);
        progressiveMapViewport.clearTiles();

        WorldPosition initialCenter = overview.playerAbsolute()
                .orElseGet(
                        () -> new WorldPosition(
                                overview.metadata().originX(),
                                0.0,
                                overview.metadata().originZ()
                        )
                );
        progressiveMapViewport.centerOn(initialCenter);
        refreshProgressiveMarkers(savePath, overview);

        ProgressiveMapSession session =
                progressiveMapSessionFactory.create(
                        savePath,
                        overview,
                        workstation.selectedRenderLayers(),
                        this::handleProgressiveMapEvent
                );
        progressiveMapSession = session;
        workstation.setProgressiveMapAvailable(true);

        RenderTileCoordinate bootstrap =
                progressiveMapSessionFactory.layout()
                        .coordinateForWorld(
                                initialCenter.x(),
                                initialCenter.z()
                        );
        session.start(
                bootstrap,
                progressiveMapViewport.currentLod()
        );
        progressiveMapViewport.refreshTileDemand();
        session.requestPlayerRings(bootstrap, 2);
    }

    private void refreshProgressiveMarkers(
            Path savePath,
            cartographer.application.WorldOverview overview
    ) {
        if (!workstation.selectedRenderLayers().contains(
                RenderLayer.MARKERS
        )) {
            progressiveMapViewport.setMarkers(List.of());
            return;
        }

        List<WorldMapMarker> markers = new ArrayList<>();
        overview.playerAbsolute().ifPresent(
                player -> markers.add(
                        new WorldMapMarker(
                                "Player",
                                player,
                                WorldMapMarker.Kind.PLAYER
                        )
                )
        );

        try {
            homeStore.load(savePath).ifPresent(
                    display -> markers.add(
                            new WorldMapMarker(
                                    "Home",
                                    overview.metadata().toAbsolute(display),
                                    WorldMapMarker.Kind.HOME
                            )
                    )
            );
            markerStore.load(savePath).forEach(
                    marker -> markers.add(
                            new WorldMapMarker(
                                    marker.name(),
                                    overview.metadata().toAbsolute(
                                            marker.position()
                                    ),
                                    WorldMapMarker.Kind.USER
                            )
                    )
            );
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Cannot load progressive map markers for {}",
                    savePath,
                    failure
            );
        }
        progressiveMapViewport.setMarkers(markers);
    }

    private void stopProgressiveMap() {
        ProgressiveMapSession current = progressiveMapSession;
        progressiveMapSession = null;
        if (current != null) {
            current.close();
        }
    }

    private void handleProgressiveTileDemand(
            WorldMapViewportRequest request
    ) {
        ProgressiveMapSession current = progressiveMapSession;
        if (current == null) {
            return;
        }
        current.requestViewport(
                request.active().visible(),
                request.active().prefetch(),
                request.missing().visible(),
                request.missing().prefetch(),
                request.active().lod()
        );
    }

    private void handleProgressiveMapEvent(
            ProgressiveMapEvent event
    ) {
        ProgressiveMapSession current = progressiveMapSession;
        if (current == null
                || current.generation() != event.generation()) {
            return;
        }

        switch (event) {
            case ProgressiveMapEvent.TileReady ready -> {
                int count = progressiveTilesReady.incrementAndGet();
                progressiveMapViewport.acceptTile(ready.tile());
                Platform.runLater(() -> {
                    ProgressiveMapSession active = progressiveMapSession;
                    if (active != null
                            && active.generation() == event.generation()) {
                        workstation.setStatus(
                                "Building map · " + count + " tiles ready"
                        );
                    }
                });
            }
            case ProgressiveMapEvent.TileFailed failed -> LOGGER.debug(
                    "Progressive tile {} unavailable: {}",
                    failed.coordinate(),
                    failed.detail()
            );
            case ProgressiveMapEvent.DiscoveryComplete _ ->
                    Platform.runLater(() -> {
                        ProgressiveMapSession active = progressiveMapSession;
                        if (active != null
                                && active.generation() == event.generation()) {
                            workstation.setStatus(
                                    "Map discovery complete · "
                                            + progressiveTilesReady.get()
                                            + " tiles ready"
                            );
                        }
                    });
            case ProgressiveMapEvent.DiscoveryFailed failed -> LOGGER.warn(
                    "Progressive observed-world discovery failed: {}",
                    failed.detail()
            );
            case ProgressiveMapEvent.SessionClosed _ -> {
                // Owner state already handles closure and supersession.
            }
        }
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
            case DISCOVERY -> surfaceToolController.handleDiscoveryCancelled();
            case LOCAL -> mapFrameController.handleLocalCancelled();
        }
        if (!operationCoordinator.isActive(WorkstationOperationScope.FOREGROUND)
                && !operationCoordinator.isActive(WorkstationOperationScope.LOCAL)
                && !operationCoordinator.isActive(WorkstationOperationScope.DISCOVERY)) {
            workstation.setStatus("Cancelled.");
        }
    }

    private void showFailure(Throwable failure) {
        LOGGER.error("Workstation operation failed", failure);
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
