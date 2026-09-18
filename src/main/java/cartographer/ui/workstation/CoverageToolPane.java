package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class CoverageToolPane extends VBox {
    CoverageToolPane() {
        super(
                4,
                new Label("EXPLORED COVERAGE"),
                new Label("Visualizes saved mapregions and holes inside their observed bounds.")
        );
    }
}
