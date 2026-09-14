package cartographer.ui.workstation;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public final class DiagnosticsPane extends VBox {
    private final Button toggle = new Button("Show technical details");
    private final VBox details = new VBox(3);

    public DiagnosticsPane() {
        super(5);
        details.setVisible(false);
        details.setManaged(false);
        toggle.setOnAction(event -> {
            boolean visible = !details.isVisible();
            details.setVisible(visible);
            details.setManaged(visible);
            toggle.setText(visible ? "Hide technical details" : "Show technical details");
        });
        getChildren().addAll(toggle, details);
    }

    public void show(List<String> lines) {
        details.getChildren().setAll(lines.stream().map(Label::new).toList());
        toggle.setDisable(lines.isEmpty());
        if (lines.isEmpty()) {
            details.setVisible(false);
            details.setManaged(false);
            toggle.setText("Show technical details");
        }
    }
}
