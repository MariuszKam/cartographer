package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

final class ProspectingToolPane extends VBox {
    private final TextField resourceField = new TextField();

    ProspectingToolPane() {
        super(4);
        resourceField.setPromptText("Resource name, or blank for all");
        getChildren().addAll(new Label("PROSPECTING"), resourceField);
    }

    String resourceText() {
        return resourceField.getText().trim();
    }

    void setBusy(boolean busy) {
        resourceField.setDisable(busy);
    }

    void setDiscoveryBusy(boolean busy) {
        resourceField.setDisable(busy);
    }
}
