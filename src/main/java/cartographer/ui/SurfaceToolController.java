package cartographer.ui;

import cartographer.application.DiscoverObservedSurfaceResourcesRequest;
import cartographer.application.DiscoverObservedSurfaceResourcesResult;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.RenderSurfaceResourceMapRequest;
import cartographer.application.RenderSurfaceResourceMapUseCase;
import cartographer.application.SurfaceDiscoveryCache;
import cartographer.application.SurfaceDiscoveryCacheKey;
import cartographer.application.SurfaceDiscoveryPolicy;
import cartographer.application.SurfaceDiscoveryRequestGate;
import cartographer.render.RenderStyle;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.SurfaceMaterialMatch;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.SurfaceObjectDiscoveryState;
import cartographer.ui.workstation.SurfaceToolMode;
import cartographer.ui.workstation.WorkstationOperationCoordinator;
import cartographer.ui.workstation.WorkstationOperationScope;
import cartographer.ui.workstation.WorkstationTool;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.workstation.WorldPanel;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class SurfaceToolController {
    private final WorkstationView workstation;
    private final SearchPanel searchPanel;
    private final WorldPanel worldPanel;
    private final WorkstationOperationCoordinator operationCoordinator;
    private final RenderSurfaceResourceMapUseCase surfaceUseCase;
    private final DiscoverObservedSurfaceResourcesUseCase discoveryUseCase;
    private final WorkstationMapFrameController mapFrameController;
    private final Consumer<Throwable> failureHandler;
    private final SurfaceDiscoveryRequestGate discoveryGate =
            new SurfaceDiscoveryRequestGate();
    private final SurfaceDiscoveryCache discoveryCache =
            new SurfaceDiscoveryCache(4);

    private DiscoverObservedSurfaceResourcesResult discoveryResult;
    private SurfaceObjectDiscoveryState discoveryState =
            SurfaceObjectDiscoveryState.NOT_SCANNED;
    private Optional<cartographer.model.WorldPosition> discoveryCenter = Optional.empty();
    private SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey discoveryTaskKey;
    private Task<DiscoverObservedSurfaceResourcesResult> discoveryTask;
    private Set<String> selectionKeys = Set.of();

    SurfaceToolController(
            WorkstationView workstation,
            SearchPanel searchPanel,
            WorldPanel worldPanel,
            WorkstationOperationCoordinator operationCoordinator,
            RenderSurfaceResourceMapUseCase surfaceUseCase,
            DiscoverObservedSurfaceResourcesUseCase discoveryUseCase,
            WorkstationMapFrameController mapFrameController,
            Consumer<Throwable> failureHandler
    ) {
        this.workstation = Objects.requireNonNull(workstation, "workstation is required");
        this.searchPanel = Objects.requireNonNull(searchPanel, "searchPanel is required");
        this.worldPanel = Objects.requireNonNull(worldPanel, "worldPanel is required");
        this.operationCoordinator = Objects.requireNonNull(
                operationCoordinator,
                "operationCoordinator is required"
        );
        this.surfaceUseCase = Objects.requireNonNull(surfaceUseCase, "surfaceUseCase is required");
        this.discoveryUseCase = Objects.requireNonNull(discoveryUseCase, "discoveryUseCase is required");
        this.mapFrameController = Objects.requireNonNull(
                mapFrameController,
                "mapFrameController is required"
        );
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler is required");
    }

    void render() {
        if (searchPanel.selectedSurfaceMode() == SurfaceToolMode.MATERIALS) {
            renderMaterial();
            return;
        }
        List<ObservedSurfaceResource> selected = selectedResourcesForRender();
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key = currentDiscoveryKey();
        if (discoveryResult == null
                || !key.equals(discoveryTaskKey)
                || !discoveryResult.observedResources().resources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(selected.stream()
                        .map(resource -> resource.candidate().qualifiedResourceKey()).toList())) {
            throw new IllegalStateException(
                    "Surface object discovery is not current; scan the save first."
            );
        }
        selected = selected.stream()
                .map(resource -> discoveryResult.observedResources()
                        .findByQualifiedResourceKey(
                                resource.candidate().qualifiedResourceKey()
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Selected surface object is not in the current discovery result: "
                                        + resource.candidate().qualifiedResourceKey())))
                .toList();
        RenderSurfaceResourceMapRequest request =
                RenderSurfaceResourceMapRequest.forObservedResources(
                        key.savePath(),
                        key.radius(),
                        1,
                        RenderStyle.TOPOGRAPHIC,
                        workstation.selectedRenderLayers(),
                        selected,
                        discoveryResult.center()
                );
        submitRender(request);
    }

    void maybeStartDiscovery() {
        if (searchPanel.selectedMode() != WorkstationTool.SURFACE
                || searchPanel.selectedSurfaceMode() != SurfaceToolMode.OBJECTS
                || worldPanel.savePathText().isBlank()) {
            return;
        }

        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentKey =
                currentDiscoveryKey();
        boolean currentTaskMatches = discoveryTaskKey != null
                && discoveryTaskKey.equals(currentKey);
        if (discoveryState.isCurrentFor(currentTaskMatches)) {
            return;
        }
        startDiscovery(currentKey.savePath());
    }

    void invalidateDiscovery() {
        discoveryGate.invalidate();
        operationCoordinator.cancel(WorkstationOperationScope.DISCOVERY);
        discoveryCache.clear();
        discoveryCenter = Optional.empty();
        discoveryResult = null;
        discoveryTaskKey = null;
        workstation.clearObservedSurfaceResources();
        discoveryState = SurfaceObjectDiscoveryState.NOT_SCANNED;
        workstation.setSurfaceObjectDiscoveryState(discoveryState);
    }

    void handleDiscoveryCancelled() {
        workstation.setDiscoveryBusy(false);
        if (discoveryState == SurfaceObjectDiscoveryState.SCANNING) {
            discoveryResult = null;
            discoveryTaskKey = null;
            workstation.clearObservedSurfaceResources();
            discoveryState = SurfaceObjectDiscoveryState.NOT_SCANNED;
            workstation.setSurfaceObjectDiscoveryState(discoveryState);
        }
    }

    private void renderMaterial() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        SurfaceMaterialMatch match = searchPanel.surfaceMaterialMatch().orElseThrow(
                () -> new IllegalStateException("Select a surface material."));
        RenderSurfaceResourceMapRequest request = new RenderSurfaceResourceMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                match,
                Optional.empty()
        );
        submitRender(request);
    }

    private void submitRender(RenderSurfaceResourceMapRequest request) {
        Optional<cartographer.ui.workstation.MapFrame> reusable =
                mapFrameController.current()
                        .filter(frame -> frame.canReusePreparedMap(
                                request.savePath(),
                                request.radius(),
                                request.pixelsPerBlock(),
                                request.style(),
                                request.center(),
                                true
                        ))
                        .filter(frame -> frame.supportsLocalRecomposition(
                                request.layers()
                        ));
        setForegroundBusy();
        if (reusable.isPresent()) {
            var frame = reusable.orElseThrow();
            workstation.setStatus("Rendering Surface from retained map data...");
            operationCoordinator.submitProgress(
                    WorkstationOperationScope.FOREGROUND,
                    "surface-retained-render",
                    progress -> surfaceUseCase.executeRetained(
                            request,
                            frame.savePath(),
                            frame.preparedMapData().orElseThrow(),
                            frame.decorationState().orElseThrow(),
                            progress
                    ),
                    result -> mapFrameController.showSurfaceResult(result, request),
                    failureHandler
            );
            return;
        }

        workstation.setStatus("Rendering surface resource...");
        operationCoordinator.submitProgress(
                WorkstationOperationScope.FOREGROUND,
                "surface-render",
                progress -> surfaceUseCase.execute(request, progress),
                result -> mapFrameController.showSurfaceResult(result, request),
                failureHandler
        );
    }

    private List<ObservedSurfaceResource> selectedResourcesForRender() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        List<ObservedSurfaceResource> selected =
                workstation.selectedObservedSurfaceResources();
        if (selected.isEmpty()) {
            throw new IllegalStateException(
                    "No observed surface object is available in this radius."
            );
        }
        return selected;
    }

    private SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey currentDiscoveryKey() {
        return new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius()
        );
    }

    private void startDiscovery(Path savePath) {
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key =
                new SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey(
                        savePath,
                        searchPanel.selectedRadius()
                );

        if (discoveryCenter.isPresent()) {
            SurfaceDiscoveryCacheKey cacheKey = SurfaceDiscoveryCacheKey.of(
                    key.savePath(),
                    key.radius(),
                    discoveryCenter.orElseThrow()
            );
            Optional<DiscoverObservedSurfaceResourcesResult> cached =
                    discoveryCache.get(cacheKey);
            if (SurfaceDiscoveryPolicy.activation(cached.isPresent(), false)
                    == SurfaceDiscoveryPolicy.Activation.CACHE_HIT) {
                discoveryGate.begin(key);
                selectionKeys = selectedResourceKeys();
                workstation.setDiscoveryBusy(false);
                applyDiscoveryResult(key, cached.orElseThrow());
                return;
            }
        }
        boolean sameKeyScanInFlight = discoveryTask != null
                && !discoveryTask.isDone()
                && key.equals(discoveryTaskKey);
        if (SurfaceDiscoveryPolicy.activation(false, sameKeyScanInFlight)
                == SurfaceDiscoveryPolicy.Activation.ALREADY_SCANNING) {
            return;
        }
        SurfaceDiscoveryRequestGate.SurfaceDiscoveryToken token =
                discoveryGate.begin(key);
        discoveryResult = null;
        discoveryTaskKey = key;
        selectionKeys = selectedResourceKeys();
        workstation.clearObservedSurfaceResources();
        discoveryState = SurfaceObjectDiscoveryState.SCANNING;
        workstation.setSurfaceObjectDiscoveryState(discoveryState);
        workstation.setDiscoveryBusy(true);
        discoveryTask = operationCoordinator.submitProgress(
                WorkstationOperationScope.DISCOVERY,
                "surface-object-discovery",
                progress -> discoveryUseCase.execute(
                        new DiscoverObservedSurfaceResourcesRequest(
                                key.savePath(),
                                key.radius(),
                                discoveryCenter
                        ),
                        progress
                ),
                result -> {
                    workstation.setDiscoveryBusy(false);
                    if (!discoveryGate.accepts(token, currentDiscoveryKey())) {
                        return;
                    }
                    if (discoveryCenter.isEmpty()) {
                        discoveryCenter = Optional.of(result.center());
                    }
                    discoveryCache.put(
                            SurfaceDiscoveryCacheKey.of(
                                    key.savePath(),
                                    key.radius(),
                                    result.center()
                            ),
                            result
                    );
                    applyDiscoveryResult(key, result);
                },
                failure -> {
                    workstation.setDiscoveryBusy(false);
                    if (!discoveryGate.accepts(token, currentDiscoveryKey())) {
                        return;
                    }
                    showDiscoveryFailure();
                }
        );
    }

    private void applyDiscoveryResult(
            SurfaceDiscoveryRequestGate.SurfaceDiscoveryKey key,
            DiscoverObservedSurfaceResourcesResult result
    ) {
        discoveryTaskKey = key;
        discoveryResult = result;
        workstation.setObservedSurfaceResources(
                result.observedResources(),
                selectionKeys
        );
        selectionKeys = selectedResourceKeys();
        discoveryState = result.observedResources().resources().isEmpty()
                ? SurfaceObjectDiscoveryState.EMPTY
                : SurfaceObjectDiscoveryState.READY;
        workstation.setSurfaceObjectDiscoveryState(discoveryState);
    }

    private Set<String> selectedResourceKeys() {
        return workstation.selectedObservedSurfaceResources().stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void setForegroundBusy() {
        if (operationCoordinator.isActive(WorkstationOperationScope.DISCOVERY)) {
            operationCoordinator.cancel(WorkstationOperationScope.DISCOVERY);
        }
        workstation.setBusy(true);
    }

    private void showDiscoveryFailure() {
        discoveryResult = null;
        discoveryState = SurfaceObjectDiscoveryState.FAILED;
        workstation.clearObservedSurfaceResources();
        workstation.setSurfaceObjectDiscoveryState(discoveryState);
    }
}
