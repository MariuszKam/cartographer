package cartographer.ui.workstation;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import cartographer.render.MapViewportGeometry;
import cartographer.render.RenderLayer;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import java.util.Set;
import java.util.function.Consumer;

public final class WorkstationView {
    private final BorderPane root = new BorderPane();
    private final WorldPanel worldPanel;
    private final ToolNavigationPane toolNavigationPane;
    private final SearchPanel searchPanel;
    private final LayerPanel layerPanel;
    private final ResultInspectorPane resultInspectorPane;
    private final MapPanel mapPanel = new MapPanel();
    private final WorkstationWorldBar worldBar = new WorkstationWorldBar();
    private final WorkstationStatusBar statusBar = new WorkstationStatusBar();
    private final VBox leftContent;
    private final ScrollPane leftScroll;
    private final VBox left;
    private final VBox rightContent;
    private final Button leftToggle = new Button("Hide tools");
    private final Button rightToggle = new Button("Hide inspector");
    private Consumer<WorkstationTool> modeListener = ignored -> { };
    private Consumer<Integer> radiusListener = ignored -> { };
    private boolean foregroundBusy;
    private boolean discoveryBusy;
    private boolean localBusy;

    public WorkstationView(Runnable onBrowse, Runnable onRender) {
        root.getStyleClass().add("workstation-root");
        worldPanel = new WorldPanel(panel -> onBrowse.run());
        searchPanel = new SearchPanel(onRender);
        toolNavigationPane = new ToolNavigationPane(this::setMode);
        layerPanel = new LayerPanel();
        resultInspectorPane = new ResultInspectorPane();
        setMode(WorkstationTool.ORE);
        searchPanel.setOnRadiusChanged(this::handleRadiusChanged);
        mapPanel.setOnZoomChanged(statusBar::setZoomFactor);

        leftContent = new VBox(8, worldPanel, toolNavigationPane, searchPanel, layerPanel);
        leftToggle.setOnAction(event -> toggleLeft());
        leftContent.getStyleClass().add("sidebar");
        leftScroll = new ScrollPane(leftContent);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(leftScroll, Priority.ALWAYS);
        left = new VBox(4, leftToggle, leftScroll);
        left.getStyleClass().add("sidebar-container");
        left.setPrefWidth(290);
        rightContent = new VBox(4, rightToggle, resultInspectorPane);
        rightContent.getStyleClass().add("inspector-container");
        rightToggle.setOnAction(event -> toggleRight());

        root.setTop(worldBar);
        root.setLeft(left);
        root.setCenter(mapPanel);
        root.setRight(rightContent);
        root.setBottom(statusBar);
        BorderPane.setMargin(root.getLeft(), new Insets(12));
        BorderPane.setMargin(mapPanel, new Insets(12, 12, 12, 0));
    }

    public Parent root() {
        return root;
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
        boolean layersVisible = mode != WorkstationTool.COVERAGE;
        layerPanel.setVisible(layersVisible);
        layerPanel.setManaged(layersVisible);
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
        toolNavigationPane.setBusy(foregroundBusy);
        searchPanel.setBusy(foregroundBusy);
        // Layer toggles remain interactive against the previously retained frame.
        refreshOperationState();
    }

    public void setDiscoveryBusy(boolean busy) {
        discoveryBusy = busy;
        worldPanel.setBusy(foregroundBusy || discoveryBusy);
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

    public void setSurfaceObjectDiscoveryState(SurfaceObjectDiscoveryState state) {
        searchPanel.setSurfaceObjectDiscoveryState(state);
    }

    public void setStatus(String text) { statusBar.setStatus(text); }
    public void setIndeterminateProgress() { statusBar.setIndeterminateProgress(); }
    public void setProgress(double completed, double total) { statusBar.setProgress(completed, total); }
    public void setSavePath(java.nio.file.Path path) { worldBar.setSavePath(path); }
    public void setPlayerLoaded(boolean loaded) { worldBar.setPlayerLoaded(loaded); }
    public void setMapGeometry(java.util.Optional<MapViewportGeometry> geometry) {
        statusBar.setMapGeometry(geometry);
    }

    public void clearMapGeometry() {
        statusBar.clearMapGeometry();
    }

    public void setCursorCoordinates(double displayX, double displayZ) {
        statusBar.setCursorCoordinates(displayX, displayZ);
    }
    public void clearCursorCoordinates() { statusBar.clearCursorCoordinates(); }

    private void toggleLeft() {
        boolean visible = leftScroll.isVisible();
        leftScroll.setVisible(!visible);
        leftScroll.setManaged(!visible);
        left.setPrefWidth(visible ? Region.USE_COMPUTED_SIZE : 290);
        leftToggle.setText(visible ? "Show tools" : "Hide tools");
    }

    private void toggleRight() {
        boolean visible = resultInspectorPane.isVisible();
        resultInspectorPane.setVisible(!visible);
        resultInspectorPane.setManaged(!visible);
        rightToggle.setText(visible ? "Show inspector" : "Hide inspector");
        if (visible) rightContent.setPrefWidth(Region.USE_COMPUTED_SIZE);
        else rightContent.setPrefWidth(290);
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

    public void setObservedSurfaceResources(ObservedSurfaceResourceCatalog catalog) {
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
