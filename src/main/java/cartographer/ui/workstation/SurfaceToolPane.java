package cartographer.ui.workstation;

import cartographer.resource.SurfaceMaterialMatch;
import cartographer.application.SurfaceMaterialPreset;
import cartographer.render.SurfaceObjectColorPolicy;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.SurfaceObjectFamily;
import cartographer.resource.SurfaceObjectFilter;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceObjectSelectionActions;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class SurfaceToolPane extends VBox {
    private final ComboBox<ObservedSurfaceResource> surfaceResourceBox = new ComboBox<>();
    private final ComboBox<SurfaceMaterialPreset> surfaceMaterialBox = new ComboBox<>();
    private final ToggleButton objectsButton = new ToggleButton("Objects");
    private final ToggleButton materialsButton = new ToggleButton("Materials");
    private final ToggleButton singleObjectButton = new ToggleButton("Single resource");
    private final ToggleButton multipleObjectButton = new ToggleButton("Multiple resources");
    private final VBox objectChecklist = new VBox(4);
    private final ScrollPane objectChecklistScroll = new ScrollPane(objectChecklist);
    private final TextField objectSearchField = new TextField();
    private final Label objectVisibilityLabel = new Label();
    private final Map<SurfaceObjectFamily, ToggleButton> familyFilters =
            new EnumMap<>(SurfaceObjectFamily.class);
    private final Button selectVisibleButton = new Button("Select visible");
    private final Button clearVisibleButton = new Button("Clear visible");
    private final Button clearAllButton = new Button("Clear all");
    private final Button resetFiltersButton = new Button("Reset filters");
    private final Map<String, ObservedSurfaceResource> objectsByKey = new LinkedHashMap<>();
    private final Map<String, CheckBox> objectChecks = new LinkedHashMap<>();
    private final Label resourceStatusLabel = new Label();
    private final Runnable availabilityChanged;
    private VBox objectsContent;
    private VBox materialsContent;
    private VBox singleObjectContent;
    private VBox multipleObjectContent;
    private SurfaceToolMode mode = SurfaceToolMode.OBJECTS;
    private SurfaceObjectDiscoveryState discoveryState = SurfaceObjectDiscoveryState.NOT_SCANNED;
    private Consumer<SurfaceToolMode> modeListener = ignored -> { };
    private boolean busy;
    private boolean discoveryBusy;
    private final boolean initialized;

    SurfaceToolPane(Runnable availabilityChanged) {
        super(4);
        this.availabilityChanged =
                availabilityChanged == null ? () -> { } : availabilityChanged;
        configure();
        build();
        initialized = true;
    }

    SurfaceToolMode selectedMode() {
        return mode;
    }

    void setOnModeChanged(Consumer<SurfaceToolMode> listener) {
        modeListener = listener == null ? ignored -> { } : listener;
    }

    Optional<SurfaceMaterialPreset> selectedMaterial() {
        return Optional.ofNullable(surfaceMaterialBox.getValue());
    }

    Optional<SurfaceMaterialMatch> materialMatch() {
        return selectedMaterial().map(preset ->
                new SurfaceMaterialMatch(preset.label(), preset.requiredTokens()));
    }

    Optional<ObservedSurfaceResource> selectedObservedSurfaceResource() {
        return Optional.ofNullable(surfaceResourceBox.getValue());
    }

    List<ObservedSurfaceResource> selectedObservedSurfaceResources() {
        if (singleObjectButton.isSelected()) {
            return selectedObservedSurfaceResource().map(List::of).orElse(List.of());
        }
        return objectChecks.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(entry -> objectsByKey.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    boolean allowsRender() {
        return mode == SurfaceToolMode.MATERIALS
                ? surfaceMaterialBox.getValue() != null
                : discoveryState.allowsRender(busy, !selectedObservedSurfaceResources().isEmpty());
    }

    void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            Set<String> previousKeys
    ) {
        List<ObservedSurfaceResource> resources = catalog.resources();
        objectsByKey.clear();
        resources.forEach(resource -> objectsByKey.put(
                resource.candidate().qualifiedResourceKey(), resource));
        objectChecks.clear();
        objectChecklist.getChildren().clear();

        for (ObservedSurfaceResource resource : resources) {
            String key = resource.candidate().qualifiedResourceKey();
            CheckBox check = new CheckBox(
                    ObservedSurfaceResourceSelection.dropdownLabel(resource)
            );
            check.setMaxWidth(Double.MAX_VALUE);
            Region color = new Region();
            Color awt = SurfaceObjectColorPolicy.colorFor(key);
            color.setPrefSize(12, 12);
            color.setStyle(
                    "-fx-background-color: rgb("
                            + awt.getRed() + "," + awt.getGreen() + "," + awt.getBlue() + ");"
            );
            check.setGraphic(color);
            check.selectedProperty().addListener(
                    (o, old, selected) -> onSelectionChanged());
            objectChecks.put(key, check);
            objectChecklist.getChildren().add(check);
        }

        surfaceResourceBox.getItems().setAll(resources);
        surfaceResourceBox.setValue(
                ObservedSurfaceResourceSelection
                        .preserve(previousKeys.stream().findFirst().orElse(""), resources)
                        .orElse(null)
        );

        if (multipleObjectButton.isSelected()) {
            String firstKey = resources.stream()
                    .findFirst()
                    .map(resource -> resource.candidate().qualifiedResourceKey())
                    .orElse("");
            Set<String> preservedKeys = ObservedSurfaceResourceSelection
                    .preserveAll(previousKeys, resources).stream()
                    .map(resource -> resource.candidate().qualifiedResourceKey())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            objectChecks.forEach((key, check) -> check.setSelected(
                    preservedKeys.contains(key)
                            || (previousKeys.isEmpty() && key.equals(firstKey))
            ));
        }

        surfaceResourceBox.setDisable(
                busy || resources.isEmpty()
                        || discoveryState != SurfaceObjectDiscoveryState.READY
        );
        updateResourceStatus();
        applyObjectFilter();
        notifyAvailabilityChanged();
    }

    void clearObservedSurfaceResources() {
        surfaceResourceBox.getItems().clear();
        surfaceResourceBox.setValue(null);
        objectsByKey.clear();
        objectChecks.clear();
        objectChecklist.getChildren().clear();
        surfaceResourceBox.setDisable(true);
        resourceStatusLabel.setText("Surface objects not scanned yet");
        applyObjectFilter();
        notifyAvailabilityChanged();
    }

    void setDiscoveryState(SurfaceObjectDiscoveryState state) {
        discoveryState = java.util.Objects.requireNonNull(state, "state is required");
        surfaceResourceBox.setDisable(
                busy || state != SurfaceObjectDiscoveryState.READY
                        || surfaceResourceBox.getItems().isEmpty()
        );
        objectChecks.values().forEach(check ->
                check.setDisable(busy || state != SurfaceObjectDiscoveryState.READY));
        resourceStatusLabel.setText(switch (state) {
            case NOT_SCANNED -> "Surface objects not scanned yet";
            case SCANNING -> "Scanning surface objects...";
            case READY -> surfaceResourceBox.getValue() == null
                    ? "No observed surface object selected."
                    : "Observed: " + surfaceResourceBox.getValue().observedCount()
                            + " occurrences";
            case EMPTY -> "No supported surface objects observed in this radius.";
            case FAILED -> "Surface object discovery unavailable.";
        });
        if (state == SurfaceObjectDiscoveryState.READY) {
            updateResourceStatus();
        }
        notifyAvailabilityChanged();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        for (javafx.scene.control.Control control : List.of(
                selectVisibleButton,
                clearVisibleButton,
                clearAllButton,
                resetFiltersButton,
                objectSearchField,
                singleObjectButton,
                multipleObjectButton,
                surfaceResourceBox,
                surfaceMaterialBox,
                objectsButton,
                materialsButton
        )) {
            control.setDisable(busy);
        }
        objectChecks.values().forEach(check -> check.setDisable(busy));
        familyFilters.values().forEach(filter -> filter.setDisable(busy));
        surfaceResourceBox.setDisable(
                busy || discoveryBusy
                        || discoveryState != SurfaceObjectDiscoveryState.READY
                        || surfaceResourceBox.getItems().isEmpty()
        );
        if (!busy && discoveryBusy) {
            setDiscoveryBusy(true);
            return;
        }
        notifyAvailabilityChanged();
    }

    void setDiscoveryBusy(boolean discoveryBusy) {
        this.discoveryBusy = discoveryBusy;
        boolean objectBusy = busy || discoveryBusy;
        for (javafx.scene.control.Control control : List.of(
                selectVisibleButton,
                clearVisibleButton,
                clearAllButton,
                resetFiltersButton,
                objectSearchField,
                singleObjectButton,
                multipleObjectButton,
                surfaceResourceBox
        )) {
            control.setDisable(objectBusy);
        }
        objectChecks.values().forEach(check ->
                check.setDisable(
                        objectBusy
                                || discoveryState != SurfaceObjectDiscoveryState.READY
                ));
        familyFilters.values().forEach(filter -> filter.setDisable(objectBusy));
        surfaceResourceBox.setDisable(
                objectBusy
                        || discoveryState != SurfaceObjectDiscoveryState.READY
                        || surfaceResourceBox.getItems().isEmpty()
        );
        objectsButton.setDisable(busy);
        materialsButton.setDisable(busy);
        surfaceMaterialBox.setDisable(busy);
        notifyAvailabilityChanged();
    }

    static List<SurfaceMaterialPreset> surfaceMaterials() {
        return List.of(
                SurfaceMaterialPreset.FIRE_CLAY,
                SurfaceMaterialPreset.CLAY,
                SurfaceMaterialPreset.PEAT
        );
    }

    private void notifyAvailabilityChanged() {
        if (initialized) {
            availabilityChanged.run();
        }
    }

    private void configure() {
        surfaceResourceBox.setEditable(false);
        surfaceResourceBox.setDisable(true);
        surfaceResourceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ObservedSurfaceResource value) {
                return value == null
                        ? ""
                        : ObservedSurfaceResourceSelection.dropdownLabel(value);
            }

            @Override
            public ObservedSurfaceResource fromString(String value) {
                return null;
            }
        });
        surfaceResourceBox.valueProperty().addListener(
                (o, old, selected) -> updateResourceStatus());

        ToggleGroup objectMode = new ToggleGroup();
        singleObjectButton.setToggleGroup(objectMode);
        multipleObjectButton.setToggleGroup(objectMode);
        singleObjectButton.setSelected(true);
        objectMode.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == multipleObjectButton
                    && objectChecks.values().stream().noneMatch(CheckBox::isSelected)
                    && surfaceResourceBox.getValue() != null) {
                String key = surfaceResourceBox.getValue()
                        .candidate()
                        .qualifiedResourceKey();
                CheckBox check = objectChecks.get(key);
                if (check != null) {
                    check.setSelected(true);
                }
            }
            updateObjectMode();
        });

        selectVisibleButton.setOnAction(e -> applyObjectSelection(
                SurfaceObjectSelectionActions.selectVisible(
                        selectedObjectKeys(), visibleObjects())));
        clearVisibleButton.setOnAction(e -> applyObjectSelection(
                SurfaceObjectSelectionActions.clearVisible(
                        selectedObjectKeys(), visibleObjects())));
        clearAllButton.setOnAction(e ->
                applyObjectSelection(SurfaceObjectSelectionActions.clearAll()));
        resetFiltersButton.setOnAction(e -> {
            objectSearchField.clear();
            familyFilters.values().forEach(filter -> filter.setSelected(false));
            applyObjectFilter();
        });

        objectSearchField.setPromptText("Search surface objects...");
        objectSearchField.textProperty().addListener(
                (o, old, value) -> applyObjectFilter());

        surfaceMaterialBox.getItems().setAll(surfaceMaterials());
        surfaceMaterialBox.setValue(SurfaceMaterialPreset.FIRE_CLAY);
        surfaceMaterialBox.setEditable(false);
        surfaceMaterialBox.valueProperty().addListener(
                (o, old, selected) -> availabilityChanged.run());

        ToggleGroup surfaceModes = new ToggleGroup();
        objectsButton.setToggleGroup(surfaceModes);
        materialsButton.setToggleGroup(surfaceModes);
        objectsButton.setSelected(true);
        surfaceModes.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == materialsButton) {
                mode = SurfaceToolMode.MATERIALS;
            } else if (selected == objectsButton) {
                mode = SurfaceToolMode.OBJECTS;
            }
            updateMode();
            modeListener.accept(mode);
            notifyAvailabilityChanged();
        });

        resourceStatusLabel.setWrapText(true);
    }

    private void build() {
        HBox objectMode = new HBox(6, singleObjectButton, multipleObjectButton);
        singleObjectContent = new VBox(4, surfaceResourceBox, resourceStatusLabel);

        FlowPane filterPane = new FlowPane(4, 4);
        for (SurfaceObjectFamily family : SurfaceObjectFamily.values()) {
            ToggleButton filter = new ToggleButton(
                    SurfaceObjectPresentation.familyLabel(family)
            );
            filter.selectedProperty().addListener(
                    (o, old, selected) -> applyObjectFilter());
            familyFilters.put(family, filter);
            filterPane.getChildren().add(filter);
        }

        multipleObjectContent = new VBox(
                4,
                objectSearchField,
                filterPane,
                objectVisibilityLabel,
                new HBox(
                        6,
                        selectVisibleButton,
                        clearVisibleButton,
                        clearAllButton,
                        resetFiltersButton
                ),
                objectChecklistScroll
        );
        objectChecklistScroll.setFitToWidth(true);
        objectChecklistScroll.setPrefViewportHeight(130);

        objectsContent = new VBox(
                4,
                new Label("SURFACE OBJECTS"),
                objectMode,
                singleObjectContent,
                multipleObjectContent
        );
        materialsContent = new VBox(
                4,
                new Label("SURFACE MATERIAL"),
                surfaceMaterialBox
        );
        getChildren().setAll(
                new HBox(6, objectsButton, materialsButton),
                objectsContent,
                materialsContent
        );
        updateObjectMode();
        updateMode();
        updateResourceStatus();
    }

    private Set<String> selectedObjectKeys() {
        return objectChecks.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<SurfaceObjectFamily> enabledFamilies() {
        return familyFilters.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<ObservedSurfaceResource> visibleObjects() {
        return SurfaceObjectFilter.visibleResources(
                objectsByKey.values(),
                objectSearchField.getText(),
                enabledFamilies()
        );
    }

    private void applyObjectFilter() {
        List<ObservedSurfaceResource> visible = visibleObjects();
        Set<String> visibleKeys = visible.stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        objectChecks.forEach((key, check) -> {
            boolean visibleEntry = visibleKeys.contains(key);
            check.setVisible(visibleEntry);
            check.setManaged(visibleEntry);
        });
        objectVisibilityLabel.setText(
                visible.isEmpty() && !objectsByKey.isEmpty()
                        ? "No surface objects match the current filters."
                        : "Showing " + visible.size() + " of "
                                + objectsByKey.size() + " observed resources"
        );
        updateResourceStatus();
        notifyAvailabilityChanged();
    }

    private void applyObjectSelection(Set<String> selectedKeys) {
        objectChecks.forEach(
                (key, check) -> check.setSelected(selectedKeys.contains(key)));
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        updateResourceStatus();
        notifyAvailabilityChanged();
    }

    private void updateResourceStatus() {
        if (discoveryState != SurfaceObjectDiscoveryState.READY) {
            return;
        }
        List<ObservedSurfaceResource> selected = selectedObservedSurfaceResources();
        if (selected.size() > 1 || multipleObjectButton.isSelected()) {
            int occurrences = selected.stream()
                    .mapToInt(ObservedSurfaceResource::observedCount)
                    .sum();
            resourceStatusLabel.setText(
                    "Selected: " + selected.size()
                            + " resources | " + occurrences + " occurrences"
            );
        } else if (!selected.isEmpty()) {
            resourceStatusLabel.setText(
                    ObservedSurfaceResourceSelection.statusText(selected.getFirst())
            );
        }
    }

    private void updateMode() {
        boolean objects = mode == SurfaceToolMode.OBJECTS;
        surfaceResourceBox.setVisible(objects);
        surfaceResourceBox.setManaged(objects);
        resourceStatusLabel.setVisible(objects);
        resourceStatusLabel.setManaged(objects);
        objectsContent.setVisible(objects);
        objectsContent.setManaged(objects);
        materialsContent.setVisible(!objects);
        materialsContent.setManaged(!objects);
        surfaceMaterialBox.setVisible(!objects);
        surfaceMaterialBox.setManaged(!objects);
    }

    private void updateObjectMode() {
        boolean single = singleObjectButton.isSelected();
        singleObjectContent.setVisible(single);
        singleObjectContent.setManaged(single);
        multipleObjectContent.setVisible(!single);
        multipleObjectContent.setManaged(!single);
        updateResourceStatus();
        notifyAvailabilityChanged();
    }
}
