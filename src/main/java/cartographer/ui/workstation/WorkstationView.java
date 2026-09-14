package cartographer.ui.workstation;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

public final class WorkstationView {
    private final BorderPane root = new BorderPane();
    private final WorldPanel worldPanel;
    private final ToolNavigationPane toolNavigationPane;
    private final SearchPanel searchPanel;
    private final MapPanel mapPanel = new MapPanel();

    public WorkstationView(Runnable onBrowse, Runnable onRender) {
        worldPanel = new WorldPanel(panel -> onBrowse.run());
        searchPanel = new SearchPanel(onRender);
        toolNavigationPane = new ToolNavigationPane(searchPanel::setMode);

        root.setLeft(new javafx.scene.layout.VBox(8, worldPanel, toolNavigationPane, searchPanel));
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

    public void setBusy(boolean busy) {
        worldPanel.setBusy(busy);
        toolNavigationPane.setBusy(busy);
        searchPanel.setBusy(busy);
    }

    public void setDiscoveryBusy(boolean busy) {
        worldPanel.setBusy(busy);
        toolNavigationPane.setBusy(busy);
        searchPanel.setDiscoveryBusy(busy);
    }

    public MapPanel mapPanel() {
        return mapPanel;
    }
}
