package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

final class RadiusPane extends VBox {
    private final ToggleButton radius128Button = new ToggleButton("128");
    private final ToggleButton radius256Button = new ToggleButton("256");
    private final ToggleButton radius512Button = new ToggleButton("512");
    private final ToggleButton radius1024Button = new ToggleButton("1024");
    private final Label radiusWarningLabel = new Label(
            "Large radius: rendering may take longer and use substantially more memory."
    );
    private Consumer<Integer> radiusListener = ignored -> { };

    RadiusPane() {
        super(4);
        ToggleGroup radiusGroup = new ToggleGroup();
        List<ToggleButton> buttons = List.of(
                radius128Button, radius256Button, radius512Button, radius1024Button
        );
        for (ToggleButton button : buttons) {
            button.setToggleGroup(radiusGroup);
            button.getStyleClass().add("radius-option");
        }
        radius256Button.setSelected(true);
        radius128Button.selectedProperty().addListener(
                (o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radius256Button.selectedProperty().addListener(
                (o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radius512Button.selectedProperty().addListener(
                (o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radius1024Button.selectedProperty().addListener((o, old, selected) -> {
            updateWarning();
            radiusListener.accept(selectedRadius());
        });

        FlowPane selector = new FlowPane(
                4, 4, radius128Button, radius256Button, radius512Button, radius1024Button
        );
        selector.getStyleClass().add("radius-selector");
        radiusWarningLabel.setVisible(false);
        radiusWarningLabel.setManaged(false);
        getChildren().addAll(new Label("RADIUS"), selector, radiusWarningLabel);
    }

    int selectedRadius() {
        if (radius128Button.isSelected()) return 128;
        if (radius512Button.isSelected()) return 512;
        if (radius1024Button.isSelected()) return 1024;
        return 256;
    }

    void setOnRadiusChanged(Consumer<Integer> listener) {
        radiusListener = listener == null ? ignored -> { } : listener;
        radiusListener.accept(selectedRadius());
    }

    void setBusy(boolean busy) {
        radius128Button.setDisable(busy);
        radius256Button.setDisable(busy);
        radius512Button.setDisable(busy);
        radius1024Button.setDisable(busy);
    }

    void setDiscoveryBusy(boolean busy) {
        // Preserve Workstation v1 behavior: radius remains interactive during discovery.
    }

    private void updateWarning() {
        boolean visible = radius1024Button.isSelected();
        radiusWarningLabel.setVisible(visible);
        radiusWarningLabel.setManaged(visible);
    }
}
