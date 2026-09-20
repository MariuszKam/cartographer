package cartographer.ui.workstation;

import cartographer.render.MapViewportGeometry;
import cartographer.ui.update.UpdateCheckView;
import cartographer.update.ApplicationVersion;
import cartographer.render.RenderLayer;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Set;
import java.util.function.Consumer;

public final class WorkstationView implements UpdateCheckView {
    private final BorderPane root = new BorderPane();
    private final BorderPane workspace = new BorderPane();
    private final WorldPanel worldPanel;
    private final ToolNavigationPane toolNavigationPane;
    private final SearchPanel searchPanel;
    private final LayerPanel layerPanel;
    private final ResultInspectorPane resultInspectorPane;
    private final MapPanel mapPanel = new MapPanel();
    private final WorkstationWorldBar worldBar;
    private final WorkstationStatusBar statusBar = new WorkstationStatusBar();
    private final VBox contextDock = new VBox(8);
    private WorkstationDockState dockState = WorkstationDockState.expanded();
    private Consumer<WorkstationTool> modeListener = ignored -> { };
    private Consumer<Integer> radiusListener = ignored -> { };
    private boolean foregroundBusy;
    private boolean discoveryBusy;
    private boolean localBusy;

    public WorkstationView(
            Runnable onBrowse,
            Runnable onRender,
            Runnable onPrepareWorld
    ) {
        root.getStyleClass().add("workstation-root");
        workspace.getStyleClass().add("workspace-body");

        worldPanel = new WorldPanel(panel -> onBrowse.run());
        worldBar = new WorkstationWorldBar(
                worldPanel,
                onPrepareWorld
        );
        searchPanel = new SearchPanel(onRender);
        layerPanel = new LayerPanel();
        resultInspectorPane = new ResultInspectorPane(layerPanel);
        toolNavigationPane = new ToolNavigationPane(this::setMode);

        searchPanel.setOnRadiusChanged(this::handleRadiusChanged);
        mapPanel.setOnZoomChanged(statusBar::setZoomFactor);
        toolNavigationPane.setOnContextToggle(this::toggleContextDock);
        toolNavigationPane.setOnInspectorToggle(this::toggleInspectorDock);

        ScrollPane contextScroll = new ScrollPane(searchPanel);
        contextScroll.setFitToWidth(true);
        contextScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contextScroll.getStyleClass().add("context-scroll");
        VBox.setVgrow(contextScroll, Priority.ALWAYS);

        Button hideControls = new Button("×");
        hideControls.getStyleClass().add("dock-close");
        hideControls.setOnAction(event -> toggleContextDock());
        Label contextHeader = new Label("SOURCE CONTROLS");
        contextHeader.getStyleClass().add("dock-title");
        HBox contextTitleBar = new HBox(8, contextHeader);
        contextTitleBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(contextHeader, Priority.ALWAYS);
        contextTitleBar.getChildren().add(hideControls);
        contextTitleBar.getStyleClass().add("dock-header");

        contextDock.getStyleClass().add("context-dock");
        contextDock.setPrefWidth(304);
        contextDock.setMinWidth(248);
        contextDock.setMaxWidth(360);
        contextDock.getChildren().addAll(contextTitleBar, contextScroll);

        resultInspectorPane.setPrefWidth(348);
        resultInspectorPane.setMinWidth(276);
        resultInspectorPane.setMaxWidth(420);

        workspace.setLeft(contextDock);
        workspace.setCenter(mapPanel);
        workspace.setRight(resultInspectorPane);
        BorderPane.setMargin(contextDock, new Insets(10, 8, 10, 10));
        BorderPane.setMargin(mapPanel, new Insets(10, 0, 10, 0));
        BorderPane.setMargin(resultInspectorPane, new Insets(10, 10, 10, 8));

        root.setTop(worldBar);
        root.setLeft(toolNavigationPane);
        root.setCenter(workspace);
        root.setBottom(statusBar);

        setMode(WorkstationTool.ORE);
        refreshDockState();
    }

    public WorkstationView(
            Runnable onBrowse,
            Runnable onRender
    ) {
        this(onBrowse, onRender, () -> { });
    }

    public Parent root() {
        return root;
    }


    @Override
    public void showCurrentVersion(ApplicationVersion version) {
        worldBar.setCurrentVersion(version);
    }

    @Override
    public void setOnCheckForUpdates(Runnable action) {
        worldBar.setOnCheckForUpdates(action);
    }

    @Override
    public void setOnOpenUpdateRelease(Runnable action) {
        worldBar.setOnOpenUpdateRelease(action);
    }

    @Override
    public void showUpdateChecking() {
        worldBar.showUpdateChecking();
    }

    @Override
    public void showUpdateAvailable(ApplicationVersion version) {
        worldBar.showUpdateAvailable(version);
    }

    @Override
    public void showUpToDate() {
        worldBar.showUpToDate();
    }

    @Override
    public void showUpdateCheckFailed(String message) {
        worldBar.showUpdateCheckFailed(message);
    }


    public WorldPanel worldPanel() {
        return worldPanel;
    }

    public SearchPanel searchPanel() {
        return searchPanel;
    }

    public ToolNavigationPane toolNavigationPane() {
        return toolNavigationPane;
    }

    private void setMode(WorkstationTool mode) {
        toolNavigationPane.setMode(mode);
        searchPanel.setMode(mode);
        layerPanel.setMode(mode);
        worldBar.setTool(mode);

        boolean layersAvailable = mode == WorkstationTool.MAP
                || mode == WorkstationTool.ORE
                || mode == WorkstationTool.SURFACE;
        resultInspectorPane.setLayersAvailable(layersAvailable);
        statusBar.setRadiusVisible(mode != WorkstationTool.COVERAGE);
        modeListener.accept(mode);
    }

    public void setOnModeChanged(Consumer<WorkstationTool> listener) {
        modeListener = listener == null ? ignored -> { } : listener;
    }

    public void setOnRadiusChanged(Consumer<Integer> listener) {
        radiusListener = listener == null ? ignored -> { } : listener;
        radiusListener.accept(searchPanel.selectedRadius());
    }

    public void setOnSurfaceModeChanged(Consumer<SurfaceToolMode> listener) {
        searchPanel.setOnSurfaceModeChanged(listener);
    }

    public void setOnRenderLayersChanged(Consumer<Set<RenderLayer>> listener) {
        layerPanel.setOnLayersChanged(listener);
    }

    private void handleRadiusChanged(int radius) {
        statusBar.setRadius(radius);
        radiusListener.accept(radius);
    }

    public void setBusy(boolean busy) {
        foregroundBusy = busy;
        worldPanel.setBusy(foregroundBusy || discoveryBusy);
        worldBar.setSnapshotSourceBusy(
                foregroundBusy || discoveryBusy
        );
        toolNavigationPane.setBusy(foregroundBusy);
        searchPanel.setBusy(foregroundBusy);
        // Layer toggles stay available against the previous retained frame.
        refreshOperationState();
    }

    public void setDiscoveryBusy(boolean busy) {
        discoveryBusy = busy;
        worldPanel.setBusy(foregroundBusy || discoveryBusy);
        worldBar.setSnapshotSourceBusy(
                foregroundBusy || discoveryBusy
        );
        searchPanel.setDiscoveryBusy(discoveryBusy);
        refreshOperationState();
    }

    public void setLocalBusy(boolean busy) {
        localBusy = busy;
        refreshOperationState();
    }

    public void setOnCancel(Runnable action) {
        statusBar.setOnCancel(action);
    }

    private void refreshOperationState() {
        boolean active = foregroundBusy || discoveryBusy || localBusy;
        statusBar.setOperationActive(active, active);
    }

    public void setSurfaceObjectDiscoveryState(
            SurfaceObjectDiscoveryState state
    ) {
        searchPanel.setSurfaceObjectDiscoveryState(state);
    }

    public void setStatus(String text) {
        statusBar.setStatus(text);
    }

    public void setIndeterminateProgress() {
        statusBar.setIndeterminateProgress();
    }

    public void setProgress(double completed, double total) {
        statusBar.setProgress(completed, total);
    }

    public void setSnapshotSaveAvailable(boolean available) {
        worldBar.setSnapshotSaveAvailable(available);
    }

    public void setSnapshotPreparing(boolean preparing) {
        worldBar.setSnapshotPreparing(preparing);
    }

    public void setSnapshotStatus(
            cartographer.application.WorldSnapshotStatus status
    ) {
        worldBar.setSnapshotStatus(status);
    }

    public void setSavePath(java.nio.file.Path path) {
        worldBar.setSavePath(path);
    }

    public void setPlayerLoaded(boolean loaded) {
        worldBar.setPlayerLoaded(loaded);
    }

    public void setMapGeometry(java.util.Optional<MapViewportGeometry> geometry) {
        statusBar.setMapGeometry(geometry);
    }

    public void clearMapGeometry() {
        statusBar.clearMapGeometry();
    }

    public void setCursorCoordinates(double displayX, double displayZ) {
        statusBar.setCursorCoordinates(displayX, displayZ);
    }

    public void clearCursorCoordinates() {
        statusBar.clearCursorCoordinates();
    }

    private void toggleContextDock() {
        dockState = dockState.toggleContext();
        refreshDockState();
    }

    private void toggleInspectorDock() {
        dockState = dockState.toggleInspector();
        refreshDockState();
    }

    private void refreshDockState() {
        workspace.setLeft(dockState.contextVisible() ? contextDock : null);
        workspace.setRight(
                dockState.inspectorVisible() ? resultInspectorPane : null
        );
        toolNavigationPane.setContextVisible(dockState.contextVisible());
        toolNavigationPane.setInspectorVisible(dockState.inspectorVisible());
    }

    public Set<RenderLayer> selectedRenderLayers() {
        return layerPanel.selectedRenderLayers();
    }

    public MapPanel mapPanel() {
        return mapPanel;
    }

    public ResultInspectorPane resultInspectorPane() {
        return resultInspectorPane;
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog
    ) {
        searchPanel.setObservedSurfaceResources(catalog);
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            java.util.Set<String> previousKeys
    ) {
        searchPanel.setObservedSurfaceResources(catalog, previousKeys);
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            String previousKey
    ) {
        searchPanel.setObservedSurfaceResources(catalog, previousKey);
    }

    public void clearObservedSurfaceResources() {
        searchPanel.clearObservedSurfaceResources();
    }

    public java.util.Optional<cartographer.resource.ObservedSurfaceResource>
    selectedObservedSurfaceResource() {
        return searchPanel.selectedObservedSurfaceResource();
    }

    public java.util.List<cartographer.resource.ObservedSurfaceResource>
    selectedObservedSurfaceResources() {
        return searchPanel.selectedObservedSurfaceResources();
    }
}
