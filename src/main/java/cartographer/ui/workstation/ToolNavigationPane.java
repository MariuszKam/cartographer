package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class ToolNavigationPane extends VBox {
    private final ToggleButton map = new ToggleButton("Map");
    private final ToggleButton coverage = new ToggleButton("Coverage");
    private final ToggleButton ores = new ToggleButton("Ores");
    private final ToggleButton surface = new ToggleButton("Surface");
    private final ToggleButton geology = new ToggleButton("Geology");
    private final ToggleButton prospecting = new ToggleButton("Prospecting");
    private final ToggleGroup group = new ToggleGroup();

    public ToolNavigationPane(Consumer<WorkstationTool> onModeChanged) {
        super(4);
        getStyleClass().add("tool-navigation");
        map.getStyleClass().add("tool-nav-item");
        coverage.getStyleClass().add("tool-nav-item");
        ores.getStyleClass().add("tool-nav-item");
        surface.getStyleClass().add("tool-nav-item");
        geology.getStyleClass().add("tool-nav-item");
        prospecting.getStyleClass().add("tool-nav-item");
        getChildren().add(new Label("MAP TOOLS"));
        getChildren().addAll(map, coverage, ores, surface, geology, prospecting);
        map.setToggleGroup(group);
        coverage.setToggleGroup(group);
        ores.setToggleGroup(group);
        surface.setToggleGroup(group);
        geology.setToggleGroup(group);
        prospecting.setToggleGroup(group);
        map.setOnAction(event -> select(WorkstationTool.MAP, onModeChanged));
        coverage.setOnAction(event -> select(WorkstationTool.COVERAGE, onModeChanged));
        ores.setOnAction(event -> select(WorkstationTool.ORE, onModeChanged));
        surface.setOnAction(event -> select(WorkstationTool.SURFACE, onModeChanged));
        geology.setOnAction(event -> select(WorkstationTool.GEOLOGY, onModeChanged));
        prospecting.setOnAction(event -> select(WorkstationTool.PROSPECTING, onModeChanged));
        setMode(WorkstationTool.ORE);
    }

    private void select(WorkstationTool mode, Consumer<WorkstationTool> onModeChanged) {
        setMode(mode);
        onModeChanged.accept(mode);
    }

    public WorkstationTool selectedMode() {
        if (group.getSelectedToggle() == map) return WorkstationTool.MAP;
        if (group.getSelectedToggle() == coverage) return WorkstationTool.COVERAGE;
        if (group.getSelectedToggle() == surface) return WorkstationTool.SURFACE;
        if (group.getSelectedToggle() == geology) return WorkstationTool.GEOLOGY;
        if (group.getSelectedToggle() == prospecting) return WorkstationTool.PROSPECTING;
        return WorkstationTool.ORE;
    }

    public void setMode(WorkstationTool mode) {
        switch (mode) {
            case MAP -> map.setSelected(true);
            case COVERAGE -> coverage.setSelected(true);
            case ORE -> ores.setSelected(true);
            case SURFACE -> surface.setSelected(true);
            case ROCK -> geology.setSelected(true);
            case PROSPECTING -> prospecting.setSelected(true);
        }
    }

    public void setBusy(boolean busy) {
        map.setDisable(busy);
        coverage.setDisable(busy);
        ores.setDisable(busy);
        surface.setDisable(busy);
        geology.setDisable(busy);
        prospecting.setDisable(busy);
    }
}
