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
    private final ToggleButton radius2048Button = new ToggleButton("2048");
    private final ToggleButton radius4096Button = new ToggleButton("4096");
    private final Label radiusWarningLabel = new Label();
    private Consumer<Integer> radiusListener = ignored -> { };

    RadiusPane() {
        super(4);
        ToggleGroup radiusGroup = new ToggleGroup();
        List<ToggleButton> buttons = List.of(
                radius128Button,
                radius256Button,
                radius512Button,
                radius1024Button,
                radius2048Button,
                radius4096Button
        );
        for (ToggleButton button : buttons) {
            button.setToggleGroup(radiusGroup);
            button.getStyleClass().add("radius-option");
        }
        radius256Button.setSelected(true);
        for (ToggleButton button : buttons) {
            button.selectedProperty().addListener((o, old, selected) -> {
                if (selected) {
                    updateWarning();
                    radiusListener.accept(selectedRadius());
                }
            });
        }

        FlowPane selector = new FlowPane(
                4,
                4,
                radius128Button,
                radius256Button,
                radius512Button,
                radius1024Button,
                radius2048Button,
                radius4096Button
        );
        selector.getStyleClass().add("radius-selector");
        radiusWarningLabel.setVisible(false);
        radiusWarningLabel.setManaged(false);
        getChildren().addAll(new Label("RADIUS"), selector, radiusWarningLabel);
    }

    int selectedRadius() {
        if (radius128Button.isSelected()) return 128;
        if (radius512Button.isSelected()) return 512;
        if (radius4096Button.isSelected()) return 4096;
        if (radius2048Button.isSelected()) return 2048;
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
        radius2048Button.setDisable(busy);
        radius4096Button.setDisable(busy);
    }

    void setDiscoveryBusy(boolean busy) {
        // Preserve Workstation v1 behavior: radius remains interactive during discovery.
    }

    static List<Integer> supportedRadii() {
        return List.of(128, 256, 512, 1024, 2048, 4096);
    }

    static String warningTextFor(int radius) {
        if (radius >= 4096) {
            return "Extreme R4096: ~8192×8192 block area; output raster is capped at "
                    + "4096×4096 (~2 blocks/pixel).";
        }
        if (radius >= 2048) {
            return "Large R2048: ~4096×4096 block area; output raster is capped at "
                    + "4096×4096 (about 1 block/pixel).";
        }
        if (radius >= 1024) {
            return "Large radius: rendering may take longer and use substantially more memory.";
        }
        return "";
    }

    private void updateWarning() {
        String text = warningTextFor(selectedRadius());
        radiusWarningLabel.setText(text);
        boolean visible = !text.isBlank();
        radiusWarningLabel.setVisible(visible);
        radiusWarningLabel.setManaged(visible);
    }
}
