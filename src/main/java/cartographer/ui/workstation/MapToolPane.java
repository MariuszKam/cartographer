package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class MapToolPane extends VBox {
    MapToolPane() {
        super(
                6,
                new Label("PROGRESSIVE MAP"),
                new Label(
                        "Terrain streams automatically from the selected save. "
                                + "Pan or zoom to prioritize another area."
                )
        );
    }
}
