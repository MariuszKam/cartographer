package cartographer.ui.workstation;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

public final class WorkstationView {
    private final BorderPane root = new BorderPane();
    private final WorldPanel worldPanel;
    private final ToolNavigationPane toolNavigationPane;
    private final SearchPanel searchPanel;
    private final LayerPanel layerPanel;
    private final MapPanel mapPanel = new MapPanel();

    public WorkstationView(Runnable onBrowse, Runnable onRender) {
        worldPanel = new WorldPanel(panel -> onBrowse.run());
        searchPanel = new SearchPanel(onRender);
        toolNavigationPane = new ToolNavigationPane(this::setMode);
        layerPanel = new LayerPanel();
        setMode(SearchPanel.SearchMode.ORE);

        root.setLeft(new javafx.scene.layout.VBox(8, worldPanel, toolNavigationPane, searchPanel, layerPanel));
        root.setCenter(mapPanel);
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
    }

    public void setDiscoveryBusy(boolean busy) {
        worldPanel.setBusy(busy);
        toolNavigationPane.setBusy(busy);
        searchPanel.setDiscoveryBusy(busy);
        layerPanel.setBusy(busy);
    }

    public Set<cartographer.render.RenderLayer> selectedRenderLayers() {
        return layerPanel.selectedRenderLayers();
    }

    public MapPanel mapPanel() {
        return mapPanel;
    }
}
