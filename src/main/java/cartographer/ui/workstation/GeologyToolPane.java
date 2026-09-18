package cartographer.ui.workstation;

import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;

final class GeologyToolPane extends VBox {
    private final RadioButton upperRockButton = new RadioButton("Upper rock");
    private final RadioButton atYButton = new RadioButton("At Y");
    private final TextField yField = new TextField();

    GeologyToolPane() {
        super(4);
        ToggleGroup group = new ToggleGroup();
        upperRockButton.setToggleGroup(group);
        atYButton.setToggleGroup(group);
        upperRockButton.setSelected(true);
        yField.setPromptText("world Y");
        atYButton.selectedProperty().addListener((o, old, selected) -> updateYField());
        getChildren().addAll(
                new Label("GEOLOGY"),
                new HBox(8, upperRockButton, atYButton),
                yField
        );
        updateYField();
    }

    boolean atY() {
        return atYButton.isSelected();
    }

    String yText() {
        return yField.getText();
    }

    void setBusy(boolean busy) {
        upperRockButton.setDisable(busy);
        atYButton.setDisable(busy);
        yField.setDisable(busy || !atYButton.isSelected());
    }

    void setDiscoveryBusy(boolean busy) {
        // Preserve Workstation v1 behavior: geology controls were not discovery-busy.
    }

    private void updateYField() {
        yField.setDisable(!atYButton.isSelected());
    }
}
