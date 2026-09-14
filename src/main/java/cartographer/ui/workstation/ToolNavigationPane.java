package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class ToolNavigationPane extends VBox {
    private final ToggleButton ores = new ToggleButton("Ores");
    private final ToggleButton surface = new ToggleButton("Surface");
    private final ToggleButton geology = new ToggleButton("Geology");
    private final ToggleButton prospecting = new ToggleButton("Prospecting");
    private final ToggleGroup group = new ToggleGroup();

    public ToolNavigationPane(Consumer<SearchPanel.SearchMode> onModeChanged) {
        super(4);
        getChildren().add(new Label("MAP TOOLS"));
        getChildren().addAll(ores, surface, geology, prospecting);
        ores.setToggleGroup(group);
        surface.setToggleGroup(group);
        geology.setToggleGroup(group);
        prospecting.setToggleGroup(group);
        ores.setOnAction(event -> select(SearchPanel.SearchMode.ORE, onModeChanged));
        surface.setOnAction(event -> select(SearchPanel.SearchMode.SURFACE, onModeChanged));
        geology.setOnAction(event -> select(SearchPanel.SearchMode.ROCK, onModeChanged));
        prospecting.setOnAction(event -> select(SearchPanel.SearchMode.PROSPECTING, onModeChanged));
        setMode(SearchPanel.SearchMode.ORE);
    }

    private void select(SearchPanel.SearchMode mode, Consumer<SearchPanel.SearchMode> onModeChanged) {
        setMode(mode);
        onModeChanged.accept(mode);
    }

    public SearchPanel.SearchMode selectedMode() {
        if (group.getSelectedToggle() == surface) return SearchPanel.SearchMode.SURFACE;
        if (group.getSelectedToggle() == geology) return SearchPanel.SearchMode.ROCK;
        if (group.getSelectedToggle() == prospecting) return SearchPanel.SearchMode.PROSPECTING;
        return SearchPanel.SearchMode.ORE;
    }

    public void setMode(SearchPanel.SearchMode mode) {
        switch (mode) {
            case ORE -> ores.setSelected(true);
            case SURFACE -> surface.setSelected(true);
            case ROCK -> geology.setSelected(true);
            case PROSPECTING -> prospecting.setSelected(true);
        }
    }

    public void setBusy(boolean busy) {
        ores.setDisable(busy);
        surface.setDisable(busy);
        geology.setDisable(busy);
        prospecting.setDisable(busy);
    }
}
