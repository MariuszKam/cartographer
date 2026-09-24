package cartographer.ui.workstation;

import cartographer.ui.OreResource;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProspectingToolPane extends VBox {
    private final RadioButton allResourcesButton = new RadioButton("All resources");
    private final RadioButton selectedResourcesButton = new RadioButton("Selected resources");
    private final VBox checklist = new VBox(4);
    private final ScrollPane checklistScroll = new ScrollPane(checklist);
    private final Button selectAllButton = new Button("Select all");
    private final Button clearButton = new Button("Clear");
    private final Map<OreResource, CheckBox> checks = new LinkedHashMap<>();
    private List<OreResource> resources = List.of();
    private boolean busy;

    ProspectingToolPane() {
        super(4);
        ToggleGroup mode = new ToggleGroup();
        allResourcesButton.setToggleGroup(mode);
        selectedResourcesButton.setToggleGroup(mode);
        allResourcesButton.setSelected(true);
        mode.selectedToggleProperty().addListener((o, old, selected) -> updateState());

        checklistScroll.setFitToWidth(true);
        checklistScroll.setPrefViewportHeight(130);
        selectAllButton.setOnAction(e ->
                checks.values().forEach(check -> check.setSelected(true)));
        clearButton.setOnAction(e ->
                checks.values().forEach(check -> check.setSelected(false)));

        getChildren().addAll(
                new Label("PROSPECTING"),
                new Label("RESOURCE SCOPE"),
                new HBox(8, allResourcesButton, selectedResourcesButton),
                new HBox(6, selectAllButton, clearButton),
                checklistScroll
        );
        updateState();
    }

    void setResources(List<OreResource> discovered) {
        resources = List.copyOf(discovered == null ? List.of() : discovered);
        checks.clear();
        checklist.getChildren().clear();
        for (OreResource resource : resources) {
            CheckBox check = new CheckBox(resource.displayName());
            checks.put(resource, check);
            checklist.getChildren().add(check);
        }
        updateState();
    }

    boolean allResources() {
        return allResourcesButton.isSelected();
    }

    List<String> selectedResourceKeys() {
        if (allResourcesButton.isSelected()) {
            return List.of();
        }
        return resources.stream()
                .filter(resource -> checks.get(resource) != null
                        && checks.get(resource).isSelected())
                .map(OreResource::sourceKey)
                .distinct()
                .toList();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateState();
    }

    private void updateState() {
        boolean selectedMode = selectedResourcesButton.isSelected();
        allResourcesButton.setDisable(busy);
        selectedResourcesButton.setDisable(busy);
        selectAllButton.setDisable(busy || !selectedMode);
        clearButton.setDisable(busy || !selectedMode);
        checklistScroll.setDisable(busy || !selectedMode);
        checks.values().forEach(check -> check.setDisable(busy || !selectedMode));
    }
}
