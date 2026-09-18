package cartographer.ui.workstation;

import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.SurfaceMaterialMatch;
import cartographer.application.SurfaceMaterialPreset;
import cartographer.model.BlockInfo;
import cartographer.render.OreOverlayPalette;
import cartographer.render.SurfaceObjectColorPolicy;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.resource.SurfaceObjectFamily;
import cartographer.resource.SurfaceObjectFilter;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceObjectSelectionActions;
import cartographer.ui.OrePreset;
import cartographer.ui.OreResource;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
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
import java.util.Set;
import java.util.EnumMap;
import java.util.function.Consumer;

public final class SearchPanel extends VBox {
    public enum SurfaceMode { OBJECTS, MATERIALS }
    private WorkstationTool mode = WorkstationTool.ORE;
    private SurfaceMode surfaceMode = SurfaceMode.OBJECTS;
    private final ComboBox<OreResource> resourceBox = new ComboBox<>();
    private final ComboBox<ObservedSurfaceResource> surfaceResourceBox = new ComboBox<>();
    private final ComboBox<SurfaceMaterialPreset> surfaceMaterialBox = new ComboBox<>();
    private final ToggleButton surfaceObjectsButton = new ToggleButton("Objects");
    private final ToggleButton surfaceMaterialsButton = new ToggleButton("Materials");
    private final ToggleButton surfaceSingleObjectButton = new ToggleButton("Single resource");
    private final ToggleButton surfaceMultipleObjectButton = new ToggleButton("Multiple resources");
    private final VBox surfaceObjectChecklist = new VBox(4);
    private final ScrollPane surfaceObjectChecklistScroll = new ScrollPane(surfaceObjectChecklist);
    private final TextField surfaceObjectSearchField = new TextField();
    private final Label surfaceObjectVisibilityLabel = new Label();
    private final Map<SurfaceObjectFamily, ToggleButton> surfaceObjectFamilyFilters = new EnumMap<>(SurfaceObjectFamily.class);
    private final Button selectVisibleSurfaceObjectsButton = new Button("Select visible");
    private final Button clearVisibleSurfaceObjectsButton = new Button("Clear visible");
    private final Button clearAllSurfaceObjectsButton = new Button("Clear all");
    private final Button resetSurfaceObjectFiltersButton = new Button("Reset filters");
    private final Map<String, ObservedSurfaceResource> surfaceObjectsByKey = new LinkedHashMap<>();
    private final Map<String, CheckBox> surfaceObjectChecks = new LinkedHashMap<>();
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
    private final RadioButton rockUpperButton = new RadioButton("Upper rock");
    private final RadioButton rockAtYButton = new RadioButton("At Y");
    private final TextField rockYField = new TextField();
    private final TextField prospectingResourceField = new TextField();
    private final RadioButton allYButton = new RadioButton("All Y");
    private final RadioButton customYButton = new RadioButton("Custom range");
    private final ToggleButton radius128Button = new ToggleButton("128");
    private final ToggleButton radius256Button = new ToggleButton("256");
    private final ToggleButton radius512Button = new ToggleButton("512");
    private final ToggleButton radius1024Button = new ToggleButton("1024");
    private final Label radiusWarningLabel = new Label("Large radius: rendering may take longer and use substantially more memory.");
    private final Button renderButton = new Button("Render");
    private final Label resourceStatusLabel = new Label();
    private final Label surfaceResourceStatusLabel = new Label();
    private final VBox modeContent = new VBox(4);
    private final VBox mapContent = new VBox(4);
    private final VBox coverageContent = new VBox(4);
    private final VBox oreContent = new VBox(4);
    private final VBox surfaceContent = new VBox(4);
    private VBox surfaceObjectsContent;
    private VBox surfaceMaterialsContent;
    private VBox surfaceSingleObjectContent;
    private VBox surfaceMultipleObjectContent;
    private final VBox rockContent = new VBox(4);
    private final VBox prospectingContent = new VBox(4);
    private VBox singleResourceContent;
    private VBox multiResourceContent;
    private VBox radiusContent;
    private VBox yFilterContent;
    private Consumer<Integer> radiusListener = ignored -> { };
    private Consumer<SurfaceMode> surfaceModeListener = ignored -> { };
    private List<OreResource> discoveredResources = List.of();
    private SurfaceObjectDiscoveryState discoveryState = SurfaceObjectDiscoveryState.NOT_SCANNED;
    private boolean globallyBusy;

    public SearchPanel(Runnable onRender) {
        configureControls(onRender);
        getStyleClass().add("tool-options");
        buildControls();
        updateResourceMode();
        getChildren().addAll(
                new Separator(),
                new HBox(8, renderButton)
        );
        setPrefWidth(270);
    }

    private void configureControls(Runnable onRender) {
        List<OreResource> presets = presetResources();
        resourceBox.getItems().setAll(presets);
        discoveredResources = presets;
        resourceBox.setValue(presets.getFirst());
        resourceBox.setEditable(true);
        resourceBox.setConverter(new StringConverter<>() {
            public String toString(OreResource value) { return value == null ? "" : value.displayName(); }
            public OreResource fromString(String value) { return resourceForDisplayName(value).orElse(null); }
        });
        resourceBox.valueProperty().addListener((o, old, selected) -> updateResourceStatus());
        resourceBox.getEditor().textProperty().addListener((o, old, typed) -> updateResourceStatus());
        surfaceResourceBox.setEditable(false);
        surfaceResourceBox.setDisable(true);
        surfaceResourceBox.setConverter(new StringConverter<>() {
            public String toString(ObservedSurfaceResource value) {
                return value == null ? "" : ObservedSurfaceResourceSelection.dropdownLabel(value);
            }
            public ObservedSurfaceResource fromString(String value) { return null; }
        });
        surfaceResourceBox.valueProperty().addListener((o, old, selected) -> updateSurfaceResourceStatus());
        ToggleGroup surfaceObjectMode = new ToggleGroup();
        surfaceSingleObjectButton.setToggleGroup(surfaceObjectMode);
        surfaceMultipleObjectButton.setToggleGroup(surfaceObjectMode);
        surfaceSingleObjectButton.setSelected(true);
        surfaceObjectMode.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == surfaceMultipleObjectButton && surfaceObjectChecks.values().stream()
                    .noneMatch(CheckBox::isSelected) && surfaceResourceBox.getValue() != null) {
                String key = surfaceResourceBox.getValue().candidate().qualifiedResourceKey();
                CheckBox check = surfaceObjectChecks.get(key);
                if (check != null) {
                    check.setSelected(true);
                }
            }
            updateSurfaceObjectMode();
        });
        selectVisibleSurfaceObjectsButton.setOnAction(e -> applySurfaceObjectSelection(
                SurfaceObjectSelectionActions.selectVisible(selectedSurfaceObjectKeys(), visibleSurfaceObjects())));
        clearVisibleSurfaceObjectsButton.setOnAction(e -> applySurfaceObjectSelection(
                SurfaceObjectSelectionActions.clearVisible(selectedSurfaceObjectKeys(), visibleSurfaceObjects())));
        clearAllSurfaceObjectsButton.setOnAction(e -> applySurfaceObjectSelection(
                SurfaceObjectSelectionActions.clearAll()));
        resetSurfaceObjectFiltersButton.setOnAction(e -> {
            surfaceObjectSearchField.clear();
            surfaceObjectFamilyFilters.values().forEach(filter -> filter.setSelected(false));
            applySurfaceObjectFilter();
        });
        surfaceObjectSearchField.setPromptText("Search surface objects...");
        surfaceObjectSearchField.textProperty().addListener((o, old, value) -> applySurfaceObjectFilter());
        surfaceMaterialBox.getItems().setAll(surfaceMaterials());
        surfaceMaterialBox.setValue(SurfaceMaterialPreset.FIRE_CLAY);
        surfaceMaterialBox.setEditable(false);
        surfaceMaterialBox.valueProperty().addListener((o, old, selected) -> updateRenderAvailability());
        ToggleGroup surfaceModes = new ToggleGroup();
        surfaceObjectsButton.setToggleGroup(surfaceModes);
        surfaceMaterialsButton.setToggleGroup(surfaceModes);
        surfaceObjectsButton.setSelected(true);
        surfaceModes.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == surfaceMaterialsButton) surfaceMode = SurfaceMode.MATERIALS;
            else if (selected == surfaceObjectsButton) surfaceMode = SurfaceMode.OBJECTS;
            updateSurfaceMode();
            surfaceModeListener.accept(surfaceMode);
        });

        ToggleGroup resourceMode = new ToggleGroup();
        singleResourceButton.setToggleGroup(resourceMode); multipleResourcesButton.setToggleGroup(resourceMode); singleResourceButton.setSelected(true);
        resourceMode.selectedToggleProperty().addListener((o, old, selected) -> updateResourceMode());
        selectAllButton.setOnAction(e -> resourceChecks.values().forEach(check -> check.setSelected(true)));
        clearAllButton.setOnAction(e -> resourceChecks.values().forEach(check -> check.setSelected(false)));

        ToggleGroup radiusGroup = new ToggleGroup();
        for (ToggleButton button : List.of(radius128Button, radius256Button, radius512Button, radius1024Button)) button.setToggleGroup(radiusGroup);
        radius256Button.setSelected(true); radius1024Button.selectedProperty().addListener((o, old, selected) -> { updateRadiusWarning(); radiusListener.accept(selectedRadius()); });
        radius128Button.selectedProperty().addListener((o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radius256Button.selectedProperty().addListener((o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radius512Button.selectedProperty().addListener((o, old, selected) -> { if (selected) radiusListener.accept(selectedRadius()); });
        radiusWarningLabel.setVisible(false); radiusWarningLabel.setManaged(false);
        ToggleGroup yGroup = new ToggleGroup(); allYButton.setToggleGroup(yGroup); customYButton.setToggleGroup(yGroup); allYButton.setSelected(true);
        yMinField.setPromptText("min"); yMaxField.setPromptText("max"); rockYField.setPromptText("world Y");
        ToggleGroup rockMode = new ToggleGroup(); rockUpperButton.setToggleGroup(rockMode); rockAtYButton.setToggleGroup(rockMode); rockUpperButton.setSelected(true);
        rockAtYButton.selectedProperty().addListener((o, old, selected) -> updateRockMode());
        allYButton.selectedProperty().addListener((o, old, selected) -> updateYFields());
        updateYFields(); updateRockMode();
        renderButton.setOnAction(e -> onRender.run());
        resourceStatusLabel.setWrapText(true); surfaceResourceStatusLabel.setWrapText(true);
        updateResourceStatus(); updateSurfaceResourceStatus();
    }

    private void buildControls() {
        HBox resourceMode = new HBox(8, singleResourceButton, multipleResourcesButton);
        singleResourceContent = new VBox(4, resourceBox, resourceStatusLabel);
        multiResourceContent = new VBox(4, new HBox(6, selectAllButton, clearAllButton), resourceChecklistScroll);
        resourceChecklistScroll.setFitToWidth(true);
        resourceChecklistScroll.setPrefViewportHeight(130);
        oreContent.getChildren().setAll(new Label("ORE SEARCH"), new Label("RESOURCE"), resourceMode,
                singleResourceContent, multiResourceContent);
        HBox surfaceObjectMode = new HBox(6, surfaceSingleObjectButton, surfaceMultipleObjectButton);
        surfaceSingleObjectContent = new VBox(4, surfaceResourceBox, surfaceResourceStatusLabel);
        FlowPane surfaceObjectFamilyFilters = new FlowPane(4, 4);
        for (SurfaceObjectFamily family : SurfaceObjectFamily.values()) {
            ToggleButton filter = new ToggleButton(SurfaceObjectPresentation.familyLabel(family));
            filter.selectedProperty().addListener((o, old, selected) -> applySurfaceObjectFilter());
            this.surfaceObjectFamilyFilters.put(family, filter);
            surfaceObjectFamilyFilters.getChildren().add(filter);
        }
        surfaceMultipleObjectContent = new VBox(4,
                surfaceObjectSearchField,
                surfaceObjectFamilyFilters,
                surfaceObjectVisibilityLabel,
                new HBox(6, selectVisibleSurfaceObjectsButton, clearVisibleSurfaceObjectsButton,
                        clearAllSurfaceObjectsButton, resetSurfaceObjectFiltersButton),
                surfaceObjectChecklistScroll);
        surfaceObjectChecklistScroll.setFitToWidth(true);
        surfaceObjectChecklistScroll.setPrefViewportHeight(130);
        surfaceObjectsContent = new VBox(4, new Label("SURFACE OBJECTS"), surfaceObjectMode,
                surfaceSingleObjectContent, surfaceMultipleObjectContent);
        surfaceMaterialsContent = new VBox(4, new Label("SURFACE MATERIAL"), surfaceMaterialBox);
        surfaceContent.getChildren().setAll(new HBox(6, surfaceObjectsButton, surfaceMaterialsButton), surfaceObjectsContent, surfaceMaterialsContent);
        rockContent.getChildren().setAll(new Label("GEOLOGY"), new HBox(8, rockUpperButton, rockAtYButton), rockYField);
        prospectingResourceField.setPromptText("Resource name, or blank for all");
        prospectingContent.getChildren().setAll(new Label("PROSPECTING"), prospectingResourceField);
        mapContent.getChildren().setAll(new Label("MAP"));
        coverageContent.getChildren().setAll(
                new Label("EXPLORED COVERAGE"),
                new Label("Visualizes saved mapregions and holes inside their observed bounds.")
        );
        modeContent.getChildren().setAll(oreContent);

        FlowPane radiusSelector = new FlowPane(4, 4, radius128Button, radius256Button, radius512Button, radius1024Button);
        radiusSelector.getStyleClass().add("radius-selector");
        for (ToggleButton button : List.of(radius128Button, radius256Button, radius512Button, radius1024Button)) {
            button.getStyleClass().add("radius-option");
        }
        radiusContent = new VBox(4, new Label("RADIUS"), radiusSelector, radiusWarningLabel);
        yFilterContent = new VBox(4, new Label("Y FILTER"), new HBox(8, allYButton, customYButton), new HBox(8, yMinField, yMaxField));
        getChildren().addAll(new Label("TOOL OPTIONS"), modeContent, radiusContent, yFilterContent);
        updateSurfaceObjectMode();
    }

    public WorkstationTool selectedMode() { return mode; }
    public void setMode(WorkstationTool selected) {
        mode = selected;
        modeContent.getChildren().setAll(switch (selected) {
            case MAP -> mapContent;
            case COVERAGE -> coverageContent;
            case ORE -> oreContent;
            case SURFACE -> surfaceContent;
            case GEOLOGY -> rockContent;
            case PROSPECTING -> prospectingContent;
        });
        yFilterContent.setManaged(selected == WorkstationTool.ORE);
        yFilterContent.setVisible(selected == WorkstationTool.ORE);
        radiusContent.setManaged(selected != WorkstationTool.COVERAGE);
        radiusContent.setVisible(selected != WorkstationTool.COVERAGE);
        renderButton.setText(selected == WorkstationTool.PROSPECTING ? "Analyze" : "Render");
        updateYFields();
        updateRockMode();
        updateResourceStatus();
        updateSurfaceResourceStatus();
        updateRenderAvailability();
        updateSurfaceMode();
    }
    public String oreResourceText() { return resourceBox.getEditor().getText().trim(); }
    public String prospectingResourceText() { return prospectingResourceField.getText().trim(); }
    public boolean customYEnabled() { return customYButton.isSelected(); }
    public boolean multipleResources() { return multipleResourcesButton.isSelected(); }
    public String yMinText() { return yMinField.getText(); }
    public String yMaxText() { return yMaxField.getText(); }
    public boolean rockAtY() { return rockAtYButton.isSelected(); }
    public String rockYText() { return rockYField.getText(); }
    public int selectedRadius() { return radius128Button.isSelected() ? 128 : radius512Button.isSelected() ? 512 : radius1024Button.isSelected() ? 1024 : 256; }
    public void setOnRadiusChanged(Consumer<Integer> listener) { radiusListener = listener == null ? ignored -> { } : listener; radiusListener.accept(selectedRadius()); }
    public void setOnSurfaceModeChanged(Consumer<SurfaceMode> listener) { surfaceModeListener = listener == null ? ignored -> { } : listener; }
    public SurfaceMode selectedSurfaceMode() { return surfaceMode; }
    public Optional<SurfaceMaterialPreset> selectedSurfaceMaterial() { return Optional.ofNullable(surfaceMaterialBox.getValue()); }
    public Optional<SurfaceMaterialMatch> surfaceMaterialMatch() {
        return selectedSurfaceMaterial().map(preset -> new SurfaceMaterialMatch(
                preset.label(), preset.requiredTokens()));
    }
    public String resourceMatch() { String editor = oreResourceText(); return resourceForDisplayName(editor).map(OreResource::match).orElse(editor); }
    public void setResources(List<OreResource> resources, Map<Integer, BlockInfo> registry) { discoveredResources = resources; rebuildResourceChecklist(); resourceBox.getItems().setAll(resources.isEmpty() ? presetResources() : resources); if (!resourceBox.getItems().isEmpty()) resourceBox.setValue(resourceBox.getItems().getFirst()); updateResourceStatus(); updateSurfaceResourceStatus(); }
    public void setDiscoveryFailure() { resourceBox.getItems().setAll(presetResources()); discoveredResources = presetResources(); rebuildResourceChecklist(); resourceBox.setValue(resourceBox.getItems().getFirst()); resourceStatusLabel.setText("Registry match: unavailable"); }

    public Optional<ObservedSurfaceResource> selectedObservedSurfaceResource() {
        return Optional.ofNullable(surfaceResourceBox.getValue());
    }

    public List<ObservedSurfaceResource> selectedObservedSurfaceResources() {
        if (surfaceSingleObjectButton.isSelected()) {
            return selectedObservedSurfaceResource().map(List::of).orElse(List.of());
        }
        return surfaceObjectChecks.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(entry -> surfaceObjectsByKey.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Set<String> selectedSurfaceObjectKeys() {
        return surfaceObjectChecks.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<SurfaceObjectFamily> enabledSurfaceObjectFamilies() {
        return surfaceObjectFamilyFilters.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<ObservedSurfaceResource> visibleSurfaceObjects() {
        return SurfaceObjectFilter.visibleResources(
                surfaceObjectsByKey.values(),
                surfaceObjectSearchField.getText(),
                enabledSurfaceObjectFamilies());
    }

    private void applySurfaceObjectFilter() {
        if (surfaceObjectVisibilityLabel == null) return;
        List<ObservedSurfaceResource> visible = visibleSurfaceObjects();
        Set<String> visibleKeys = visible.stream()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        surfaceObjectChecks.forEach((key, check) -> {
            boolean isVisible = visibleKeys.contains(key);
            check.setVisible(isVisible);
            check.setManaged(isVisible);
        });
        surfaceObjectVisibilityLabel.setText(visible.isEmpty() && !surfaceObjectsByKey.isEmpty()
                ? "No surface objects match the current filters."
                : "Showing " + visible.size() + " of " + surfaceObjectsByKey.size()
                        + " observed resources");
        updateSurfaceResourceStatus();
        updateRenderAvailability();
    }

    private void applySurfaceObjectSelection(Set<String> selectedKeys) {
        surfaceObjectChecks.forEach((key, check) -> check.setSelected(selectedKeys.contains(key)));
        onSurfaceObjectSelectionChanged();
    }

    public void setObservedSurfaceResources(ObservedSurfaceResourceCatalog catalog) {
        String previousKey = selectedObservedSurfaceResource()
                .map(resource -> resource.candidate().qualifiedResourceKey())
                .orElse("");
        setObservedSurfaceResources(catalog, previousKey);
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            java.util.Set<String> previousKeys
    ) {
        List<ObservedSurfaceResource> resources = catalog.resources();
        surfaceObjectsByKey.clear();
        resources.forEach(resource -> surfaceObjectsByKey.put(
                resource.candidate().qualifiedResourceKey(), resource));
        surfaceObjectChecks.clear();
        surfaceObjectChecklist.getChildren().clear();
        for (ObservedSurfaceResource resource : resources) {
            String key = resource.candidate().qualifiedResourceKey();
            CheckBox check = new CheckBox(ObservedSurfaceResourceSelection.dropdownLabel(resource));
            check.setMaxWidth(Double.MAX_VALUE);
            Region color = new Region();
            Color awt = SurfaceObjectColorPolicy.colorFor(key);
            color.setPrefSize(12, 12);
            color.setStyle("-fx-background-color: rgb(" + awt.getRed() + "," + awt.getGreen() + "," + awt.getBlue() + ");");
            check.setGraphic(color);
            check.selectedProperty().addListener((o, old, selected) -> onSurfaceObjectSelectionChanged());
            surfaceObjectChecks.put(key, check);
            surfaceObjectChecklist.getChildren().add(check);
        }
        surfaceResourceBox.getItems().setAll(resources);
        surfaceResourceBox.setValue(ObservedSurfaceResourceSelection
                .preserve(previousKeys.stream().findFirst().orElse(""), resources)
                .orElse(null));
        if (surfaceMultipleObjectButton.isSelected()) {
            String firstKey = resources.stream().findFirst()
                    .map(resource -> resource.candidate().qualifiedResourceKey()).orElse("");
            java.util.Set<String> preservedKeys = ObservedSurfaceResourceSelection
                    .preserveAll(previousKeys, resources).stream()
                    .map(resource -> resource.candidate().qualifiedResourceKey())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            surfaceObjectChecks.forEach((key, check) -> check.setSelected(
                    preservedKeys.contains(key) || (previousKeys.isEmpty() && key.equals(firstKey))));
        }
        surfaceResourceBox.setDisable(globallyBusy || resources.isEmpty()
                || discoveryState != SurfaceObjectDiscoveryState.READY);
        updateSurfaceResourceStatus();
        updateRenderAvailability();
        applySurfaceObjectFilter();
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            String previousKey
    ) {
        setObservedSurfaceResources(catalog,
                previousKey == null || previousKey.isBlank()
                        ? java.util.Set.of() : java.util.Set.of(previousKey));
    }

    public void clearObservedSurfaceResources() {
        surfaceResourceBox.getItems().clear();
        surfaceResourceBox.setValue(null);
        surfaceObjectsByKey.clear();
        surfaceObjectChecks.clear();
        surfaceObjectChecklist.getChildren().clear();
        surfaceResourceBox.setDisable(true);
        surfaceResourceStatusLabel.setText("Surface objects not scanned yet");
        applySurfaceObjectFilter();
        updateRenderAvailability();
    }

    public void setSurfaceObjectDiscoveryState(SurfaceObjectDiscoveryState state) {
        discoveryState = java.util.Objects.requireNonNull(state, "state is required");
        surfaceResourceBox.setDisable(globallyBusy || state != SurfaceObjectDiscoveryState.READY
                || surfaceResourceBox.getItems().isEmpty());
        surfaceObjectChecks.values().forEach(check -> check.setDisable(
                globallyBusy || state != SurfaceObjectDiscoveryState.READY));
        surfaceResourceStatusLabel.setText(switch (state) {
            case NOT_SCANNED -> "Surface objects not scanned yet";
            case SCANNING -> "Scanning surface objects...";
            case READY -> surfaceResourceBox.getValue() == null
                    ? "No observed surface object selected."
                    : "Observed: " + surfaceResourceBox.getValue().observedCount() + " occurrences";
            case EMPTY -> "No supported surface objects observed in this radius.";
            case FAILED -> "Surface object discovery unavailable.";
        });
        if (state == SurfaceObjectDiscoveryState.READY) {
            updateSurfaceResourceStatus();
        }
        updateRenderAvailability();
    }

    public void setBusy(boolean busy) {
        globallyBusy = busy;
        for (javafx.scene.control.Control control : List.of(renderButton, selectAllButton, clearAllButton, selectVisibleSurfaceObjectsButton, clearVisibleSurfaceObjectsButton, clearAllSurfaceObjectsButton, resetSurfaceObjectFiltersButton, surfaceObjectSearchField, radius128Button, radius256Button, radius512Button, radius1024Button, allYButton, customYButton, singleResourceButton, multipleResourcesButton, surfaceSingleObjectButton, surfaceMultipleObjectButton, resourceBox, surfaceResourceBox, surfaceMaterialBox, surfaceObjectsButton, surfaceMaterialsButton, rockUpperButton, rockAtYButton, rockYField, prospectingResourceField, yMinField, yMaxField)) control.setDisable(busy);
        surfaceObjectChecks.values().forEach(check -> check.setDisable(busy));
        surfaceObjectFamilyFilters.values().forEach(filter -> filter.setDisable(busy));
        surfaceResourceBox.setDisable(busy || discoveryState != SurfaceObjectDiscoveryState.READY || surfaceResourceBox.getItems().isEmpty());
        yMinField.setDisable(busy || allYButton.isSelected() || mode != WorkstationTool.ORE); yMaxField.setDisable(busy || allYButton.isSelected() || mode != WorkstationTool.ORE);
        rockYField.setDisable(busy || !rockAtYButton.isSelected() || mode != WorkstationTool.GEOLOGY);
        updateRenderAvailability();
    }
    public void setDiscoveryBusy(boolean busy) { globallyBusy = busy; renderButton.setDisable(busy); resourceBox.setDisable(busy); surfaceResourceBox.setDisable(busy || discoveryState != SurfaceObjectDiscoveryState.READY || surfaceResourceBox.getItems().isEmpty()); surfaceMaterialBox.setDisable(busy); surfaceObjectsButton.setDisable(busy); surfaceMaterialsButton.setDisable(busy); surfaceSingleObjectButton.setDisable(busy); surfaceMultipleObjectButton.setDisable(busy); selectVisibleSurfaceObjectsButton.setDisable(busy); clearVisibleSurfaceObjectsButton.setDisable(busy); clearAllSurfaceObjectsButton.setDisable(busy); resetSurfaceObjectFiltersButton.setDisable(busy); surfaceObjectSearchField.setDisable(busy); surfaceObjectFamilyFilters.values().forEach(filter -> filter.setDisable(busy)); prospectingResourceField.setDisable(busy); singleResourceButton.setDisable(busy); multipleResourcesButton.setDisable(busy); selectAllButton.setDisable(busy); clearAllButton.setDisable(busy); surfaceObjectChecks.values().forEach(check -> check.setDisable(busy)); updateRenderAvailability(); }


    public List<ActualOreOverlaySpec> selectedOverlays() {
        if (singleResourceButton.isSelected()) { String match = resourceMatch(); if (match.isBlank()) return List.of(); OreResource selected = resourceForDisplayName(resourceBox.getEditor().getText()).orElse(null); Color color = selected == null ? OreOverlayPalette.colorFor(match, 0) : resourceColors.getOrDefault(selected, OreOverlayPalette.colorFor(match, 0)); return List.of(new ActualOreOverlaySpec(selected == null ? match : selected.displayName(), match, color, selected != null && selected.registryVerified() ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING)); }
        return discoveredResources.stream().filter(resource -> resourceChecks.get(resource) != null && resourceChecks.get(resource).isSelected()).map(resource -> new ActualOreOverlaySpec(resource.displayName(), resource.match(), resourceColors.getOrDefault(resource, OreOverlayPalette.colorFor(resource.match(), 0)), resource.registryVerified() ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING)).toList();
    }

    private void updateSurfaceResourceStatus() {
        if (mode != WorkstationTool.SURFACE) return;
        if (discoveryState == SurfaceObjectDiscoveryState.READY) {
            List<ObservedSurfaceResource> selected = selectedObservedSurfaceResources();
            if (selected.size() > 1 || surfaceMultipleObjectButton.isSelected()) {
                int occurrences = selected.stream().mapToInt(ObservedSurfaceResource::observedCount).sum();
                surfaceResourceStatusLabel.setText("Selected: " + selected.size()
                        + " resources | " + occurrences + " occurrences");
            } else if (!selected.isEmpty()) {
                surfaceResourceStatusLabel.setText(ObservedSurfaceResourceSelection.statusText(selected.getFirst()));
            }
        }
    }
    private void onSurfaceObjectSelectionChanged() {
        updateSurfaceResourceStatus();
        updateRenderAvailability();
    }
    private Optional<OreResource> resourceForDisplayName(String value) { return resourceBox.getItems().stream().filter(resource -> resource.displayName().equalsIgnoreCase(value.trim())).findFirst(); }
    private List<OreResource> presetResources() { return Arrays.stream(OrePreset.values()).map(preset -> new OreResource(preset.label(), preset.match(), preset.match(), false, 0)).toList(); }
    static List<SurfaceMaterialPreset> surfaceMaterials() {
        return List.of(SurfaceMaterialPreset.FIRE_CLAY, SurfaceMaterialPreset.CLAY, SurfaceMaterialPreset.PEAT);
    }

    private void updateYFields() { boolean disabled = allYButton.isSelected() || mode != WorkstationTool.ORE; yMinField.setDisable(disabled); yMaxField.setDisable(disabled); updateRockMode(); }
    private void updateRockMode() { rockYField.setDisable(mode != WorkstationTool.GEOLOGY || !rockAtYButton.isSelected()); }
    private void updateRadiusWarning() { boolean visible = radius1024Button.isSelected(); radiusWarningLabel.setVisible(visible); radiusWarningLabel.setManaged(visible); }
    private void updateResourceMode() {
        boolean multiple = multipleResourcesButton.isSelected();
        if (singleResourceContent != null) {
            singleResourceContent.setVisible(!multiple);
            singleResourceContent.setManaged(!multiple);
        }
        if (multiResourceContent != null) {
            multiResourceContent.setVisible(multiple);
            multiResourceContent.setManaged(multiple);
        }
        if (multiple) {
            String match = resourceMatch();
            resourceChecks.forEach((resource, check) -> check.setSelected(resource.match().equalsIgnoreCase(match)));
        }
        updateResourceStatus();
    }
    private void updateResourceStatus() { if (mode != WorkstationTool.ORE) return; Optional<OreResource> selected = resourceForDisplayName(resourceBox.getEditor().getText()); resourceStatusLabel.setText(selected.isEmpty() ? "Registry match: custom input" : selected.get().registryVerified() ? "Registry match: verified (" + selected.get().registryMatchCount() + " block codes)" : "Registry match: not verified - using \"" + selected.get().match() + "\" as custom match"); }
    private void updateSurfaceMode() {
        boolean objects = surfaceMode == SurfaceMode.OBJECTS;
        surfaceResourceBox.setVisible(objects); surfaceResourceBox.setManaged(objects);
        surfaceResourceStatusLabel.setVisible(objects); surfaceResourceStatusLabel.setManaged(objects);
        surfaceObjectsContent.setVisible(objects); surfaceObjectsContent.setManaged(objects);
        surfaceMaterialsContent.setVisible(!objects); surfaceMaterialsContent.setManaged(!objects);
        surfaceMaterialBox.setVisible(!objects); surfaceMaterialBox.setManaged(!objects);
        updateRenderAvailability();
    }
    private void updateSurfaceObjectMode() {
        boolean single = surfaceSingleObjectButton.isSelected();
        surfaceSingleObjectContent.setVisible(single);
        surfaceSingleObjectContent.setManaged(single);
        surfaceMultipleObjectContent.setVisible(!single);
        surfaceMultipleObjectContent.setManaged(!single);
        updateSurfaceResourceStatus();
        updateRenderAvailability();
    }
    private void updateRenderAvailability() {
        if (globallyBusy) { renderButton.setDisable(true); return; }
        if (mode != WorkstationTool.SURFACE) { renderButton.setDisable(false); return; }
        boolean valid = surfaceMode == SurfaceMode.MATERIALS
                ? surfaceMaterialBox.getValue() != null
                : discoveryState.allowsRender(globallyBusy, !selectedObservedSurfaceResources().isEmpty());
        renderButton.setDisable(!valid);
    }
    private void rebuildResourceChecklist() { resourceChecks.clear(); resourceColors.clear(); resourceChecklist.getChildren().clear(); for (int index = 0; index < discoveredResources.size(); index++) { OreResource resource = discoveredResources.get(index); CheckBox check = new CheckBox(resource.displayName()); Region color = new Region(); color.setPrefSize(12, 12); Color awt = OreOverlayPalette.colorFor(resource.match(), index); resourceColors.put(resource, awt); color.setStyle("-fx-background-color: rgb(" + awt.getRed() + "," + awt.getGreen() + "," + awt.getBlue() + ");"); check.setGraphic(color); resourceChecks.put(resource, check); resourceChecklist.getChildren().add(check); } }
}
