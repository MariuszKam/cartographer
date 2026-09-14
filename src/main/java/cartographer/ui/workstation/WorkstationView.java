package cartographer.ui.workstation;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

public final class WorkstationView {
    private final BorderPane root = new BorderPane();
    private final WorldPanel worldPanel;
    private final SearchPanel searchPanel;
    private final MapPanel mapPanel = new MapPanel();

    public WorkstationView(Runnable onBrowse, Runnable onRender) {
        worldPanel = new WorldPanel(panel -> onBrowse.run());
        searchPanel = new SearchPanel(onRender);

        root.setLeft(new javafx.scene.layout.VBox(8, worldPanel, searchPanel));
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

    public MapPanel mapPanel() {
        return mapPanel;
    }
}
