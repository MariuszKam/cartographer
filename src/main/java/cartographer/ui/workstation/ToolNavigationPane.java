package cartographer.ui.workstation;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

public final class ToolNavigationPane extends VBox {
    private final ToggleButton map = tool("Map", "Base map and retained layers");
    private final ToggleButton coverage = tool("Coverage", "Observed mapregion coverage");
    private final ToggleButton ores = tool("Ores", "Actual ore overlays");
    private final ToggleButton surface = tool("Surface", "Surface objects and materials");
    private final ToggleButton geology = tool("Geology", "ROCK map");
    private final ToggleButton prospecting = tool("Prospect", "Fused geology + ore prospecting");
    private final ToggleGroup group = new ToggleGroup();
    private final Button contextToggle = new Button("Controls");
    private final Button inspectorToggle = new Button("Inspector");
    private Runnable contextAction = () -> { };
    private Runnable inspectorAction = () -> { };

    public ToolNavigationPane(Consumer<WorkstationTool> onModeChanged) {
        super(6);
        Objects.requireNonNull(onModeChanged, "onModeChanged is required");
        getStyleClass().addAll("tool-navigation", "tool-rail");
        setPrefWidth(108);
        setMinWidth(96);
        setMaxWidth(118);

        Label title = new Label("TOOLS");
        title.getStyleClass().add("tool-rail-title");

        getChildren().add(title);
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

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        contextToggle.getStyleClass().add("rail-secondary");
        inspectorToggle.getStyleClass().add("rail-secondary");
        contextToggle.setMaxWidth(Double.MAX_VALUE);
        inspectorToggle.setMaxWidth(Double.MAX_VALUE);
        contextToggle.setTooltip(new Tooltip("Show or hide tool controls"));
        inspectorToggle.setTooltip(new Tooltip("Show or hide inspector dock"));
        contextToggle.setOnAction(event -> contextAction.run());
        inspectorToggle.setOnAction(event -> inspectorAction.run());

        getChildren().addAll(
                spacer,
                new Separator(),
                contextToggle,
                inspectorToggle
        );
        setMode(WorkstationTool.ORE);
    }

    private ToggleButton tool(String text, String tooltip) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("tool-nav-item");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void select(
            WorkstationTool mode,
            Consumer<WorkstationTool> onModeChanged
    ) {
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
            case GEOLOGY -> geology.setSelected(true);
            case PROSPECTING -> prospecting.setSelected(true);
        }
    }

    public void setOnContextToggle(Runnable action) {
        contextAction = action == null ? () -> { } : action;
    }

    public void setOnInspectorToggle(Runnable action) {
        inspectorAction = action == null ? () -> { } : action;
    }

    public void setContextVisible(boolean visible) {
        contextToggle.setText(visible ? "Hide controls" : "Controls");
        contextToggle.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("active"),
                visible
        );
    }

    public void setInspectorVisible(boolean visible) {
        inspectorToggle.setText(visible ? "Hide inspect" : "Inspector");
        inspectorToggle.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("active"),
                visible
        );
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
