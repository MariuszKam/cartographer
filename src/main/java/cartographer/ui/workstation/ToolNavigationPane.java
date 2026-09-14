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

    public ToolNavigationPane(Consumer<SearchPanel.SearchMode> onModeChanged) {
        super(4);
        getChildren().add(new Label("MAP TOOLS"));
        getChildren().addAll(ores, surface, geology, prospecting);
        ToggleGroup group = new ToggleGroup();
        ores.setToggleGroup(group);
        surface.setToggleGroup(group);
        geology.setToggleGroup(group);
        prospecting.setToggleGroup(group);
        ores.setSelected(true);
        ores.setOnAction(event -> onModeChanged.accept(SearchPanel.SearchMode.ORE));
        surface.setOnAction(event -> onModeChanged.accept(SearchPanel.SearchMode.SURFACE));
        geology.setOnAction(event -> onModeChanged.accept(SearchPanel.SearchMode.ROCK));
        prospecting.setOnAction(event -> onModeChanged.accept(SearchPanel.SearchMode.PROSPECTING));
    }

    public void setBusy(boolean busy) {
        ores.setDisable(busy);
        surface.setDisable(busy);
        geology.setDisable(busy);
        prospecting.setDisable(busy);
    }
}
