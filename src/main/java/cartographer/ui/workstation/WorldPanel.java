package cartographer.ui.workstation;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.function.Consumer;

public final class WorldPanel extends GridPane {
    private final TextField saveField = new TextField();
    private final Button browseButton = new Button("Browse...");
    private final Label playerStatusLabel = new Label("Player: not loaded");

    public WorldPanel(Consumer<WorldPanel> onBrowse) {
        setHgap(8);
        setVgap(8);
        add(new Label("SAVE"), 0, 0);
        add(saveField, 0, 1, 2, 1);
        add(browseButton, 1, 1);
        add(new Label("PLAYER"), 0, 2);
        add(playerStatusLabel, 0, 3, 2, 1);

        saveField.setEditable(false);
        saveField.setPromptText("Select a .vcdbs save");
        playerStatusLabel.setWrapText(true);
        browseButton.setOnAction(event -> onBrowse.accept(this));
    }

    public String savePathText() { return saveField.getText(); }
    public void setSavePath(String path) { saveField.setText(path); }

    public void setPlayerStatus(String text) {
        playerStatusLabel.setText(text);
    }

    public void setBusy(boolean busy) {
        saveField.setDisable(busy);
        browseButton.setDisable(busy);
    }
}
