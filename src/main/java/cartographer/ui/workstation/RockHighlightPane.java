package cartographer.ui.workstation;

import cartographer.geology.rock.RockIdentity;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class RockHighlightPane extends VBox {
    private static final String ALL = "All rocks";
    private final ComboBox<String> rockBox = new ComboBox<>();
    private Consumer<Optional<String>> listener = ignored -> { };
    private boolean updating;

    RockHighlightPane() {
        super(4);
        rockBox.getItems().setAll(ALL);
        rockBox.setValue(ALL);
        rockBox.valueProperty().addListener((o, old, selected) -> {
            if (!updating) {
                listener.accept(selectedRockCode());
            }
        });
        getChildren().addAll(new Label("ROCK HIGHLIGHT"), rockBox);
        setVisible(false);
        setManaged(false);
    }

    void setRocks(List<RockIdentity> rocks) {
        updating = true;
        try {
            String previous = rockBox.getValue();
        List<String> codes = rocks == null
                ? List.of()
                : rocks.stream()
                .map(RockIdentity::code)
                .distinct()
                .sorted()
                .toList();
        rockBox.getItems().setAll(
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(ALL),
                        codes.stream()
                ).toList()
        );
            rockBox.setValue(
                    previous != null && rockBox.getItems().contains(previous)
                            ? previous
                            : ALL
            );
        } finally {
            updating = false;
        }
    }

    Optional<String> selectedRockCode() {
        String selected = rockBox.getValue();
        return selected == null || ALL.equals(selected)
                ? Optional.empty()
                : Optional.of(selected);
    }

    void setOnChanged(Consumer<Optional<String>> listener) {
        this.listener = listener == null ? ignored -> { } : listener;
    }

    void setModeVisible(boolean visible) {
        setVisible(visible);
        setManaged(visible);
    }

}
