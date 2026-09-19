package cartographer.ui.workstation;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.Objects;
import java.util.function.Consumer;

public final class WorldPanel extends GridPane {
    private final TextField saveField = new TextField();
    private final Button browseButton = new Button("Open save");
    private final Label playerStatusLabel = new Label("Player: not loaded");

    public WorldPanel(Consumer<WorldPanel> onBrowse) {
        Objects.requireNonNull(onBrowse, "onBrowse is required");
        setHgap(8);
        setVgap(0);
        getStyleClass().addAll("world-details", "world-chooser");

        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints pathColumn = new ColumnConstraints();
        pathColumn.setHgrow(Priority.ALWAYS);
        pathColumn.setFillWidth(true);
        ColumnConstraints browseColumn = new ColumnConstraints();
        ColumnConstraints playerColumn = new ColumnConstraints();
        getColumnConstraints().addAll(
                labelColumn,
                pathColumn,
                browseColumn,
                playerColumn
        );

        Label saveLabel = new Label("SAVE");
        saveLabel.getStyleClass().add("world-field-label");
        playerStatusLabel.getStyleClass().add("world-player-status");

        add(saveLabel, 0, 0);
        add(saveField, 1, 0);
        add(browseButton, 2, 0);
        add(playerStatusLabel, 3, 0);

        saveField.setEditable(false);
        saveField.setMaxWidth(Double.MAX_VALUE);
        saveField.setPromptText("Select a Vintage Story .vcdbs save");
        playerStatusLabel.setWrapText(false);
        browseButton.setOnAction(event -> onBrowse.accept(this));
    }

    public String savePathText() {
        return saveField.getText();
    }

    public void setSavePath(String path) {
        saveField.setText(path);
    }

    public void setPlayerStatus(String text) {
        playerStatusLabel.setText(text);
    }

    public void setBusy(boolean busy) {
        saveField.setDisable(busy);
        browseButton.setDisable(busy);
    }
}
