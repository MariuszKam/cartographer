package cartographer.ui.workstation;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
    private final VBox rightContent;
    private final Button leftToggle = new Button("Hide tools");
    private final Button rightToggle = new Button("Hide inspector");

    public WorkstationView(Runnable onBrowse, Runnable onRender) {
        root.getStyleClass().add("workstation-root");
        worldPanel = new WorldPanel(panel -> onBrowse.run());
        searchPanel = new SearchPanel(onRender);
        toolNavigationPane = new ToolNavigationPane(this::setMode);
        layerPanel = new LayerPanel();
        resultInspectorPane = new ResultInspectorPane();
        setMode(SearchPanel.SearchMode.ORE);
        searchPanel.setOnRadiusChanged(statusBar::setRadius);
        mapPanel.setOnZoomChanged(statusBar::setZoomFactor);

        leftContent = new VBox(8, worldPanel, toolNavigationPane, searchPanel, layerPanel);
        leftToggle.setOnAction(event -> toggleLeft());
        leftContent.getStyleClass().add("sidebar");
        VBox left = new VBox(4, leftToggle, leftContent);
        left.getStyleClass().add("sidebar");
        rightContent = new VBox(4, rightToggle, resultInspectorPane);
        rightContent.getStyleClass().add("result-inspector");
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

    private void setMode(SearchPanel.SearchMode mode) {
        toolNavigationPane.setMode(mode);
        searchPanel.setMode(mode);
        layerPanel.setMode(mode);
    }

    public void setBusy(boolean busy) {
        worldPanel.setBusy(busy);
        toolNavigationPane.setBusy(busy);
        searchPanel.setBusy(busy);
        layerPanel.setBusy(busy);
        statusBar.setBusy(busy);
    }

    public void setDiscoveryBusy(boolean busy) {
        worldPanel.setBusy(busy);
        toolNavigationPane.setBusy(busy);
        searchPanel.setDiscoveryBusy(busy);
        layerPanel.setBusy(busy);
        statusBar.setBusy(false);
    }

    public void setStatus(String text) { statusBar.setStatus(text); }
    public void setSavePath(java.nio.file.Path path) { worldBar.setSavePath(path); }
    public void setPlayerLoaded(boolean loaded) { worldBar.setPlayerLoaded(loaded); }

    private void toggleLeft() {
        boolean visible = leftContent.isVisible();
        leftContent.setVisible(!visible);
        leftContent.setManaged(!visible);
        leftToggle.setText(visible ? "Show tools" : "Hide tools");
    }

    private void toggleRight() {
        boolean visible = resultInspectorPane.isVisible();
        resultInspectorPane.setVisible(!visible);
        resultInspectorPane.setManaged(!visible);
        rightToggle.setText(visible ? "Show inspector" : "Hide inspector");
        if (visible) rightContent.setPrefWidth(Region.USE_COMPUTED_SIZE);
        else rightContent.setPrefWidth(310);
    }

    public Set<cartographer.render.RenderLayer> selectedRenderLayers() {
        return layerPanel.selectedRenderLayers();
    }

    public MapPanel mapPanel() {
        return mapPanel;
    }

    public ResultInspectorPane resultInspectorPane() {
        return resultInspectorPane;
    }
}
