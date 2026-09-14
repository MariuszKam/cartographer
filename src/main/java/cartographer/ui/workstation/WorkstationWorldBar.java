package cartographer.ui.workstation;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.nio.file.Path;

public final class WorkstationWorldBar extends HBox {
    private final Label save = new Label("Save: —");
    private final Label player = new Label("Player: unavailable");

    public WorkstationWorldBar() {
        super(12);
        getChildren().addAll(new Label("VS Cartographer"), save, player);
    }

    public void setSavePath(Path path) {
        save.setText("Save: " + path.getFileName());
    }

    public void setPlayerLoaded(boolean loaded) {
        player.setText("Player: " + (loaded ? "loaded" : "unavailable"));
    }
}
