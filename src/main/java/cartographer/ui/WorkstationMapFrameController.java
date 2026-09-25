package cartographer.ui;

import cartographer.application.ProspectingAreaRequest;
import cartographer.application.ProspectingAreaResult;
import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderCoverageMapRequest;
import cartographer.application.RenderCoverageMapResult;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.application.RenderSurfaceResourceMapRequest;
import cartographer.application.RenderSurfaceResourceMapResult;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.ui.workstation.LocalRecompositionGate;
import cartographer.ui.workstation.MapCursorPosition;
import cartographer.ui.workstation.MapFrame;
import cartographer.ui.workstation.MapFrameCompositor;
import cartographer.ui.workstation.MapFrameState;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.ResultInspectorPane;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.WorkstationOperationCoordinator;
import cartographer.ui.workstation.WorkstationOperationScope;
import cartographer.ui.workstation.WorkstationTool;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.workstation.WorldPanel;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class WorkstationMapFrameController {
    private final WorkstationView workstation;
    private final SearchPanel searchPanel;
    private final WorldPanel worldPanel;
    private final MapPanel mapPanel;
    private final ResultInspectorPane resultInspector;
    private final WorkstationOperationCoordinator operationCoordinator;
    private final RenderRockMapUseCase rockUseCase;
    private final Supplier<Optional<WorldPosition>> loadedPlayerAbsolute;
    private final Supplier<Optional<WorldMetadata>> loadedWorldMetadata;
    private final MapFrameState mapFrameState = new MapFrameState();
    private final MapFrameCompositor mapFrameCompositor = new MapFrameCompositor();
    private final LocalRecompositionGate localRecompositionGate =
            new LocalRecompositionGate();
    private long rockHighlightGeneration;

    WorkstationMapFrameController(
            WorkstationView workstation,
            SearchPanel searchPanel,
            WorldPanel worldPanel,
            MapPanel mapPanel,
            ResultInspectorPane resultInspector,
            WorkstationOperationCoordinator operationCoordinator,
            RenderRockMapUseCase rockUseCase,
            Supplier<Optional<WorldPosition>> loadedPlayerAbsolute,
            Supplier<Optional<WorldMetadata>> loadedWorldMetadata
    ) {
        this.workstation = Objects.requireNonNull(workstation, "workstation is required");
        this.searchPanel = Objects.requireNonNull(searchPanel, "searchPanel is required");
        this.worldPanel = Objects.requireNonNull(worldPanel, "worldPanel is required");
        this.mapPanel = Objects.requireNonNull(mapPanel, "mapPanel is required");
        this.resultInspector = Objects.requireNonNull(resultInspector, "resultInspector is required");
        this.operationCoordinator = Objects.requireNonNull(
                operationCoordinator,
                "operationCoordinator is required"
        );
        this.rockUseCase = Objects.requireNonNull(rockUseCase, "rockUseCase is required");
        this.loadedPlayerAbsolute = Objects.requireNonNull(
                loadedPlayerAbsolute,
                "loadedPlayerAbsolute supplier is required"
        );
        this.loadedWorldMetadata = Objects.requireNonNull(
                loadedWorldMetadata,
                "loadedWorldMetadata supplier is required"
        );
    }

    Optional<MapFrame> current() {
        return mapFrameState.current();
    }

    void reset() {
        localRecompositionGate.invalidate();
        rockHighlightGeneration++;
        mapFrameState.clear();
        workstation.clearMapGeometry();
    }

    void handleModeChanged(WorkstationTool mode) {
        if (mode != WorkstationTool.GEOLOGY
                && mode != WorkstationTool.PROSPECTING) {
            return;
        }
        List<cartographer.geology.rock.RockIdentity> rocks =
                mapFrameState.current()
                        .flatMap(MapFrame::rockMap)
                        .map(cartographer.geology.rock.RockMap::ordinalTable)
                        .orElseGet(List::of);
        searchPanel.setRockLegend(rocks);
    }

    void showOreResult(
            RenderActualOreMapResult result,
            RenderActualOreMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(
                result.image(),
                Optional.of(result.geometry()),
                loadedPlayerAbsolute.get()
        );
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
        workstation.setBusy(false);
    }

    void showMapResult(
            RenderActualOreMapResult result,
            RenderActualOreMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(
                result.image(),
                Optional.of(result.geometry()),
                loadedPlayerAbsolute.get()
        );
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
        workstation.setBusy(false);
    }

    void showCoverageResult(
            RenderCoverageMapResult result,
            RenderCoverageMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(result.image(), result.geometry(), loadedPlayerAbsolute.get());
        mapFrameState.clear();
        result.geometry().ifPresent(geometry ->
                mapFrameState.retain(MapFrame.coverage(request.savePath(), geometry)));
        workstation.setMapGeometry(result.geometry());
        resultInspector.showCoverageResult(result);
        workstation.setStatus("Coverage rendered.");
        workstation.setBusy(false);
    }

    void showSurfaceResult(
            RenderSurfaceResourceMapResult result,
            RenderSurfaceResourceMapRequest request
    ) {
        localRecompositionGate.invalidate();
        mapPanel.show(
                result.image(),
                Optional.of(result.geometry()),
                loadedPlayerAbsolute.get()
        );
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
        workstation.setBusy(false);
    }

    void showRockResult(
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
                loadedPlayerAbsolute.get()
        );
        mapFrameState.retain(MapFrame.geology(
                request.savePath(),
                displayed.geometry(),
                rockMap
        ));
        workstation.setMapGeometry(Optional.of(displayed.geometry()));
        resultInspector.showRockResult(result, request);
        workstation.setStatus("Rock map rendered.");
        workstation.setBusy(false);
    }

    void showProspectingResult(
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
                    loadedPlayerAbsolute.get()
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
        workstation.setBusy(false);
    }

    void handleRenderLayersChanged(Set<RenderLayer> layers) {
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
                            loadedPlayerAbsolute.get()
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

    void handleCursorPositionChanged(Optional<MapCursorPosition> cursor) {
        Optional<WorldMetadata> metadata = loadedWorldMetadata.get();
        if (cursor.isEmpty() || metadata.isEmpty()) {
            workstation.clearCursorCoordinates();
            resultInspector.clearCursorInspection();
            return;
        }
        MapCursorPosition absolute = cursor.orElseThrow();
        var display = metadata.orElseThrow().toDisplay(
                new WorldPosition(
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

    void handleRockHighlightChanged(Optional<String> rockCode) {
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
                            loadedPlayerAbsolute.get()
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

    void handleLocalCancelled() {
        localRecompositionGate.invalidate();
        rockHighlightGeneration++;
        workstation.setLocalBusy(false);
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

    private String conciseMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
