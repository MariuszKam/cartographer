package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public final class DiagnosticsPane extends VBox {
    private final VBox details = new VBox(5);

    public DiagnosticsPane() {
        super(8);
        getStyleClass().add("diagnostics");
        Label title = new Label("TECHNICAL DIAGNOSTICS");
        title.getStyleClass().add("diagnostics-title");
        getChildren().addAll(title, details);
        show(List.of());
    }

    public void show(List<String> lines) {
        List<String> safe = lines == null ? List.of() : List.copyOf(lines);
        if (safe.isEmpty()) {
            Label empty = new Label("No diagnostics for the current result.");
            empty.getStyleClass().add("empty-state");
            details.getChildren().setAll(empty);
            return;
        }
        details.getChildren().setAll(safe.stream().map(line -> {
            Label label = new Label(line);
            label.setWrapText(true);
            label.getStyleClass().add("diagnostic-line");
            return label;
        }).toList());
    }
}
