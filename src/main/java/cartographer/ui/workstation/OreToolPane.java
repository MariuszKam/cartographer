package cartographer.ui.workstation;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.render.OreOverlayPalette;
import cartographer.ui.OrePreset;
import cartographer.ui.OreResource;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OreToolPane extends VBox {
    private final ComboBox<OreResource> resourceBox = new ComboBox<>();
    private final RadioButton singleResourceButton = new RadioButton("Single resource");
    private final RadioButton multipleResourcesButton = new RadioButton("Multiple resources");
    private final VBox resourceChecklist = new VBox(4);
    private final ScrollPane resourceChecklistScroll = new ScrollPane(resourceChecklist);
    private final Button selectAllButton = new Button("Select all");
    private final Button clearAllButton = new Button("Clear");
    private final Map<OreResource, CheckBox> resourceChecks = new LinkedHashMap<>();
    private final Map<OreResource, Color> resourceColors = new LinkedHashMap<>();
    private final TextField yMinField = new TextField();
    private final TextField yMaxField = new TextField();
    private final RadioButton allYButton = new RadioButton("All Y");
    private final RadioButton customYButton = new RadioButton("Custom range");
    private final Label resourceStatusLabel = new Label();
    private VBox singleResourceContent;
    private VBox multiResourceContent;
    private List<OreResource> discoveredResources = List.of();
    private boolean busy;

    OreToolPane() {
        super(4);
        configure();
        build();
    }

    String oreResourceText() {
        return resourceBox.getEditor().getText().trim();
    }

    boolean customYEnabled() {
        return customYButton.isSelected();
    }

    boolean multipleResources() {
        return multipleResourcesButton.isSelected();
    }

    String yMinText() {
        return yMinField.getText();
    }

    String yMaxText() {
        return yMaxField.getText();
    }

    String resourceMatch() {
        String editor = oreResourceText();
        return resourceForDisplayName(editor).map(OreResource::match).orElse(editor);
    }

    void setResources(List<OreResource> resources) {
        discoveredResources = List.copyOf(resources);
        rebuildResourceChecklist();
        resourceBox.getItems().setAll(resources.isEmpty() ? presetResources() : resources);
        if (!resourceBox.getItems().isEmpty()) {
            resourceBox.setValue(resourceBox.getItems().getFirst());
        }
        updateResourceStatus();
    }

    void setDiscoveryFailure() {
        List<OreResource> presets = presetResources();
        discoveredResources = presets;
        rebuildResourceChecklist();
        resourceBox.getItems().setAll(presets);
        resourceBox.setValue(presets.getFirst());
        resourceStatusLabel.setText("Registry match: unavailable");
    }

    List<ActualOreOverlaySpec> selectedOverlays() {
        if (singleResourceButton.isSelected()) {
            String match = resourceMatch();
            if (match.isBlank()) {
                return List.of();
            }
            OreResource selected = resourceForDisplayName(resourceBox.getEditor().getText())
                    .orElse(null);
            Color color = selected == null
                    ? OreOverlayPalette.colorFor(match, 0)
                    : resourceColors.getOrDefault(selected, OreOverlayPalette.colorFor(match, 0));
            return List.of(new ActualOreOverlaySpec(
                    selected == null ? match : selected.displayName(),
                    match,
                    color,
                    selected != null && selected.registryVerified()
                            ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE
                            : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING
            ));
        }
        return discoveredResources.stream()
                .filter(resource -> resourceChecks.get(resource) != null
                        && resourceChecks.get(resource).isSelected())
                .map(resource -> new ActualOreOverlaySpec(
                        resource.displayName(),
                        resource.match(),
                        resourceColors.getOrDefault(
                                resource,
                                OreOverlayPalette.colorFor(resource.match(), 0)
                        ),
                        resource.registryVerified()
                                ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE
                                : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING
                ))
                .toList();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        singleResourceButton.setDisable(busy);
        multipleResourcesButton.setDisable(busy);
        selectAllButton.setDisable(busy);
        clearAllButton.setDisable(busy);
        resourceBox.setDisable(busy);
        allYButton.setDisable(busy);
        customYButton.setDisable(busy);
        resourceChecks.values().forEach(check -> check.setDisable(busy));
        updateYFields();
    }

    private void configure() {
        List<OreResource> presets = presetResources();
        discoveredResources = presets;
        resourceBox.getItems().setAll(presets);
        resourceBox.setValue(presets.getFirst());
        resourceBox.setEditable(true);
        resourceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(OreResource value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public OreResource fromString(String value) {
                return resourceForDisplayName(value).orElse(null);
            }
        });
        resourceBox.valueProperty().addListener((o, old, selected) -> updateResourceStatus());
        resourceBox.getEditor().textProperty().addListener((o, old, typed) -> updateResourceStatus());

        ToggleGroup resourceMode = new ToggleGroup();
        singleResourceButton.setToggleGroup(resourceMode);
        multipleResourcesButton.setToggleGroup(resourceMode);
        singleResourceButton.setSelected(true);
        resourceMode.selectedToggleProperty().addListener((o, old, selected) -> updateResourceMode());
        selectAllButton.setOnAction(e ->
                resourceChecks.values().forEach(check -> check.setSelected(true)));
        clearAllButton.setOnAction(e ->
                resourceChecks.values().forEach(check -> check.setSelected(false)));

        ToggleGroup yGroup = new ToggleGroup();
        allYButton.setToggleGroup(yGroup);
        customYButton.setToggleGroup(yGroup);
        allYButton.setSelected(true);
        allYButton.selectedProperty().addListener((o, old, selected) -> updateYFields());
        yMinField.setPromptText("min");
        yMaxField.setPromptText("max");
        resourceStatusLabel.setWrapText(true);
    }

    private void build() {
        HBox resourceMode = new HBox(8, singleResourceButton, multipleResourcesButton);
        singleResourceContent = new VBox(4, resourceBox, resourceStatusLabel);
        multiResourceContent = new VBox(
                4,
                new HBox(6, selectAllButton, clearAllButton),
                resourceChecklistScroll
        );
        resourceChecklistScroll.setFitToWidth(true);
        resourceChecklistScroll.setPrefViewportHeight(130);
        VBox yFilter = new VBox(
                4,
                new Label("Y FILTER"),
                new HBox(8, allYButton, customYButton),
                new HBox(8, yMinField, yMaxField)
        );
        getChildren().setAll(
                new Label("ORE SEARCH"),
                new Label("RESOURCE"),
                resourceMode,
                singleResourceContent,
                multiResourceContent,
                yFilter
        );
        updateResourceMode();
        updateYFields();
        updateResourceStatus();
    }

    private void updateResourceMode() {
        boolean multiple = multipleResourcesButton.isSelected();
        singleResourceContent.setVisible(!multiple);
        singleResourceContent.setManaged(!multiple);
        multiResourceContent.setVisible(multiple);
        multiResourceContent.setManaged(multiple);
        if (multiple) {
            String match = resourceMatch();
            resourceChecks.forEach(
                    (resource, check) -> check.setSelected(
                            resource.match().equalsIgnoreCase(match)
                    )
            );
        }
        updateResourceStatus();
    }

    private void updateYFields() {
        boolean disabled = busy || allYButton.isSelected();
        yMinField.setDisable(disabled);
        yMaxField.setDisable(disabled);
    }

    private void updateResourceStatus() {
        Optional<OreResource> selected =
                resourceForDisplayName(resourceBox.getEditor().getText());
        resourceStatusLabel.setText(
                selected.map(resource -> resource.registryVerified()
                                ? "Registry match: verified ("
                                        + resource.registryMatchCount() + " block codes)"
                                : "Registry match: not verified - using \""
                                        + resource.match() + "\" as custom match")
                        .orElse("Registry match: custom input")
        );
    }

    private Optional<OreResource> resourceForDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        return resourceBox.getItems().stream()
                .filter(resource -> resource.displayName().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private List<OreResource> presetResources() {
        return Arrays.stream(OrePreset.values())
                .map(preset -> new OreResource(
                        preset.label(),
                        preset.match(),
                        preset.match(),
                        false,
                        0
                ))
                .toList();
    }

    private void rebuildResourceChecklist() {
        resourceChecks.clear();
        resourceColors.clear();
        resourceChecklist.getChildren().clear();
        for (int index = 0; index < discoveredResources.size(); index++) {
            OreResource resource = discoveredResources.get(index);
            CheckBox check = new CheckBox(resource.displayName());
            Region color = new Region();
            color.setPrefSize(12, 12);
            Color awt = OreOverlayPalette.colorFor(resource.match(), index);
            resourceColors.put(resource, awt);
            color.setStyle(
                    "-fx-background-color: rgb("
                            + awt.getRed() + "," + awt.getGreen() + "," + awt.getBlue() + ");"
            );
            check.setGraphic(color);
            resourceChecks.put(resource, check);
            resourceChecklist.getChildren().add(check);
        }
    }
}
