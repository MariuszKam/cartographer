package cartographer.ui.workstation;

import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.SurfaceResourceMatch;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import cartographer.render.OreOverlayPalette;
import cartographer.render.RockLegendEntry;
import cartographer.resource.ResourceAnalyzer;
import cartographer.ui.OrePreset;
import cartographer.ui.OreResource;
import cartographer.ui.SurfaceResourcePreset;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class SearchPanel extends VBox {
    public enum SearchMode { ORE, SURFACE, ROCK, PROSPECTING }
    private final RadioButton oreSearchButton = new RadioButton("Ore deposits");
    private final RadioButton surfaceSearchButton = new RadioButton("Surface resources");
    private final RadioButton rockSearchButton = new RadioButton("Rock geology");
    private final RadioButton prospectingSearchButton = new RadioButton("Prospecting");
    private final ComboBox<OreResource> resourceBox = new ComboBox<>();
    private final ComboBox<SurfaceResourcePreset> surfaceResourceBox = new ComboBox<>();
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
    private final VBox rockLegendBox = new VBox(4);
    private final ScrollPane rockLegendScroll = new ScrollPane(rockLegendBox);
    private final TextField prospectingResourceField = new TextField();
    private final VBox prospectingResultsBox = new VBox(4);
    private final RadioButton allYButton = new RadioButton("All Y");
    private final RadioButton customYButton = new RadioButton("Custom range");
    private final RadioButton radius128Button = new RadioButton("128");
    private final RadioButton radius256Button = new RadioButton("256");
    private final RadioButton radius512Button = new RadioButton("512");
    private final RadioButton radius1024Button = new RadioButton("1024");
    private final Label radiusWarningLabel = new Label("Large radius: rendering may take longer and use substantially more memory.");
    private final Button renderButton = new Button("Render");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Label resourceStatusLabel = new Label();
    private final Label surfaceResourceStatusLabel = new Label();
    private final Label resultLabel = new Label("Select a save and render an ore map.");
    private List<OreResource> discoveredResources = List.of();
    private Map<Integer, BlockInfo> loadedRegistry = Map.of();

    public SearchPanel(Runnable onRender) {
        configureControls(onRender);
        getChildren().addAll(
                buildControls(),
                new Separator(),
                new HBox(8, renderButton, progress),
                statusLabel,
                resultLabel
        );
        setPrefWidth(280);
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
        surfaceResourceBox.getItems().setAll(List.of(SurfaceResourcePreset.values()));
        surfaceResourceBox.setEditable(true);
        surfaceResourceBox.setConverter(new StringConverter<>() {
            public String toString(SurfaceResourcePreset value) { return value == null ? "" : value.label(); }
            public SurfaceResourcePreset fromString(String value) { return surfacePresetFor(value).orElse(null); }
        });
        surfaceResourceBox.setValue(SurfaceResourcePreset.FIRE_CLAY);
        surfaceResourceBox.getEditor().textProperty().addListener((o, old, typed) -> updateSurfaceResourceStatus());

        ToggleGroup searchType = new ToggleGroup();
        for (RadioButton button : List.of(oreSearchButton, surfaceSearchButton, rockSearchButton, prospectingSearchButton)) button.setToggleGroup(searchType);
        oreSearchButton.setSelected(true);
        searchType.selectedToggleProperty().addListener((o, old, selected) -> updateSearchType());
        ToggleGroup resourceMode = new ToggleGroup();
        singleResourceButton.setToggleGroup(resourceMode); multipleResourcesButton.setToggleGroup(resourceMode); singleResourceButton.setSelected(true);
        resourceMode.selectedToggleProperty().addListener((o, old, selected) -> updateResourceMode());
        selectAllButton.setOnAction(e -> resourceChecks.values().forEach(check -> check.setSelected(true)));
        clearAllButton.setOnAction(e -> resourceChecks.values().forEach(check -> check.setSelected(false)));

        ToggleGroup radiusGroup = new ToggleGroup();
        for (RadioButton button : List.of(radius128Button, radius256Button, radius512Button, radius1024Button)) button.setToggleGroup(radiusGroup);
        radius256Button.setSelected(true); radius1024Button.selectedProperty().addListener((o, old, selected) -> updateRadiusWarning());
        radiusWarningLabel.setVisible(false); radiusWarningLabel.setManaged(false);
        ToggleGroup yGroup = new ToggleGroup(); allYButton.setToggleGroup(yGroup); customYButton.setToggleGroup(yGroup); allYButton.setSelected(true);
        yMinField.setPromptText("min"); yMaxField.setPromptText("max"); rockYField.setPromptText("world Y");
        ToggleGroup rockMode = new ToggleGroup(); rockUpperButton.setToggleGroup(rockMode); rockAtYButton.setToggleGroup(rockMode); rockUpperButton.setSelected(true);
        rockAtYButton.selectedProperty().addListener((o, old, selected) -> updateRockMode());
        allYButton.selectedProperty().addListener((o, old, selected) -> updateYFields());
        updateYFields(); updateRockMode();
        renderButton.setOnAction(e -> onRender.run());
        progress.setVisible(false); progress.setPrefSize(28, 28);
        statusLabel.setWrapText(true); resourceStatusLabel.setWrapText(true); surfaceResourceStatusLabel.setWrapText(true); resultLabel.setWrapText(true);
        updateResourceStatus(); updateSurfaceResourceStatus();
    }

    private GridPane buildControls() {
        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
        grid.add(new Label("SEARCH TYPE"), 0, 0);
        grid.add(new FlowPane(8, 4, oreSearchButton, surfaceSearchButton, rockSearchButton, prospectingSearchButton), 0, 1, 2, 1);
        grid.add(new Label("RESOURCE"), 0, 2);
        HBox resourceMode = new HBox(8, singleResourceButton, multipleResourcesButton); grid.add(resourceMode, 0, 3, 2, 1);
        VBox single = new VBox(4, resourceBox, resourceStatusLabel); grid.add(single, 0, 4, 2, 1);
        VBox multi = new VBox(4, new HBox(6, selectAllButton, clearAllButton), resourceChecklistScroll); grid.add(multi, 0, 5, 2, 1);
        resourceChecklistScroll.setFitToWidth(true); resourceChecklistScroll.setPrefViewportHeight(130);
        single.managedProperty().bind(single.visibleProperty()); multi.managedProperty().bind(multi.visibleProperty());
        resourceMode.visibleProperty().bind(oreSearchButton.selectedProperty()); resourceMode.managedProperty().bind(resourceMode.visibleProperty());
        single.visibleProperty().bind(oreSearchButton.selectedProperty().and(singleResourceButton.selectedProperty()));
        multi.visibleProperty().bind(oreSearchButton.selectedProperty().and(multipleResourcesButton.selectedProperty()));
        VBox surface = new VBox(4, surfaceResourceBox, surfaceResourceStatusLabel); grid.add(surface, 0, 3, 2, 3); surface.visibleProperty().bind(surfaceSearchButton.selectedProperty()); surface.managedProperty().bind(surface.visibleProperty());
        VBox rock = new VBox(4, new HBox(8, rockUpperButton, rockAtYButton), rockYField, rockLegendScroll); grid.add(rock, 0, 3, 2, 3); rock.visibleProperty().bind(rockSearchButton.selectedProperty()); rock.managedProperty().bind(rock.visibleProperty());
        VBox prospecting = new VBox(4, prospectingResourceField, prospectingResultsBox); prospectingResourceField.setPromptText("Resource name, or blank for all"); grid.add(prospecting, 0, 3, 2, 3); prospecting.visibleProperty().bind(prospectingSearchButton.selectedProperty()); prospecting.managedProperty().bind(prospecting.visibleProperty());
        grid.add(new Label("RADIUS"), 0, 6); grid.add(new FlowPane(8, 4, radius128Button, radius256Button, radius512Button, radius1024Button), 0, 7, 2, 1); grid.add(radiusWarningLabel, 0, 8, 2, 1);
        grid.add(new Label("Y FILTER"), 0, 9); grid.add(allYButton, 0, 10); grid.add(customYButton, 1, 10); grid.add(yMinField, 0, 11); grid.add(yMaxField, 1, 11);
        return grid;
    }

    public SearchMode selectedMode() { return oreSearchButton.isSelected() ? SearchMode.ORE : surfaceSearchButton.isSelected() ? SearchMode.SURFACE : rockSearchButton.isSelected() ? SearchMode.ROCK : SearchMode.PROSPECTING; }
    public String oreResourceText() { return resourceBox.getEditor().getText().trim(); }
    public String surfaceResourceText() { return surfaceResourceBox.getEditor().getText().trim(); }
    public String prospectingResourceText() { return prospectingResourceField.getText().trim(); }
    public boolean customYEnabled() { return customYButton.isSelected(); }
    public boolean multipleResources() { return multipleResourcesButton.isSelected(); }
    public String yMinText() { return yMinField.getText(); }
    public String yMaxText() { return yMaxField.getText(); }
    public String rockYText() { return rockYField.getText(); }
    public int selectedRadius() { return radius128Button.isSelected() ? 128 : radius512Button.isSelected() ? 512 : radius1024Button.isSelected() ? 1024 : 256; }
    public String resourceMatch() { String editor = oreResourceText(); return resourceForDisplayName(editor).map(OreResource::match).orElse(editor); }
    public void setStatus(String text) { statusLabel.setText(text); }
    public void setResult(String text) { resultLabel.setText(text); }
    public void setResources(List<OreResource> resources, Map<Integer, BlockInfo> registry) { discoveredResources = resources; loadedRegistry = registry; rebuildResourceChecklist(); resourceBox.getItems().setAll(resources.isEmpty() ? presetResources() : resources); if (!resourceBox.getItems().isEmpty()) resourceBox.setValue(resourceBox.getItems().getFirst()); updateResourceStatus(); updateSurfaceResourceStatus(); }
    public void setDiscoveryFailure() { resourceBox.getItems().setAll(presetResources()); discoveredResources = presetResources(); loadedRegistry = Map.of(); rebuildResourceChecklist(); resourceBox.setValue(resourceBox.getItems().getFirst()); resourceStatusLabel.setText("Registry match: unavailable"); }

    public void setBusy(boolean busy) {
        for (javafx.scene.control.Control control : List.of(renderButton, selectAllButton, clearAllButton, radius128Button, radius256Button, radius512Button, radius1024Button, allYButton, customYButton, oreSearchButton, surfaceSearchButton, rockSearchButton, prospectingSearchButton, singleResourceButton, multipleResourcesButton, resourceBox, surfaceResourceBox, rockUpperButton, rockAtYButton, rockYField, prospectingResourceField, yMinField, yMaxField)) control.setDisable(busy);
        yMinField.setDisable(busy || allYButton.isSelected() || surfaceSearchButton.isSelected()); yMaxField.setDisable(busy || allYButton.isSelected() || surfaceSearchButton.isSelected());
        rockYField.setDisable(busy || !rockAtYButton.isSelected() || !rockSearchButton.isSelected()); progress.setVisible(busy);
    }
    public void setDiscoveryBusy(boolean busy) { renderButton.setDisable(busy); resourceBox.setDisable(busy); surfaceResourceBox.setDisable(busy); oreSearchButton.setDisable(busy); surfaceSearchButton.setDisable(busy); rockSearchButton.setDisable(busy); prospectingSearchButton.setDisable(busy); prospectingResourceField.setDisable(busy); singleResourceButton.setDisable(busy); multipleResourcesButton.setDisable(busy); selectAllButton.setDisable(busy); clearAllButton.setDisable(busy); progress.setVisible(false); }

    public void addProspectingResult(String text) { prospectingResultsBox.getChildren().add(new Label(text)); }
    public void clearProspectingResults() { prospectingResultsBox.getChildren().clear(); }
    public void clearRockLegend() { rockLegendBox.getChildren().clear(); }
    public void addRockLegend(RockLegendEntry entry) { Region swatch = new Region(); swatch.setPrefSize(12, 12); int rgb = entry.argb(); swatch.setStyle("-fx-background-color: rgb(" + ((rgb >> 16) & 0xff) + "," + ((rgb >> 8) & 0xff) + "," + (rgb & 0xff) + ");"); Label label = new Label(entry.rock().code() + " (" + entry.observedCellCount() + ", " + String.format(java.util.Locale.ROOT, "%.2f%%", entry.observedPercentage()) + ")"); rockLegendBox.getChildren().add(new HBox(6, swatch, label)); }

    public List<ActualOreOverlaySpec> selectedOverlays() {
        if (singleResourceButton.isSelected()) { String match = resourceMatch(); if (match.isBlank()) return List.of(); OreResource selected = resourceForDisplayName(resourceBox.getEditor().getText()).orElse(null); Color color = selected == null ? OreOverlayPalette.colorFor(match, 0) : resourceColors.getOrDefault(selected, OreOverlayPalette.colorFor(match, 0)); return List.of(new ActualOreOverlaySpec(selected == null ? match : selected.displayName(), match, color, selected != null && selected.registryVerified() ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING)); }
        return discoveredResources.stream().filter(resource -> resourceChecks.get(resource) != null && resourceChecks.get(resource).isSelected()).map(resource -> new ActualOreOverlaySpec(resource.displayName(), resource.match(), resourceColors.getOrDefault(resource, OreOverlayPalette.colorFor(resource.match(), 0)), resource.registryVerified() ? cartographer.scanner.ActualBlockMatchMode.ORE_CODE : cartographer.scanner.ActualBlockMatchMode.GENERIC_SUBSTRING)).toList();
    }

    public Optional<SurfaceResourcePreset> surfacePresetFor(String value) { return Arrays.stream(SurfaceResourcePreset.values()).filter(preset -> preset.label().equalsIgnoreCase(value.trim())).findFirst(); }
    private void updateSurfaceResourceStatus() { if (!surfaceSearchButton.isSelected()) return; Optional<SurfaceResourcePreset> preset = surfacePresetFor(surfaceResourceText()); if (preset.isEmpty()) { surfaceResourceStatusLabel.setText("Registry candidates: custom input"); return; } SurfaceResourceMatch match = new SurfaceResourceMatch(preset.get().label(), preset.get().requiredTokens(), preset.get().acceptedCodePrefixes()); long candidates = loadedRegistry.values().stream().map(block -> new SurfaceBlock(0, 0, 0, block)).filter(match::matches).count(); surfaceResourceStatusLabel.setText("Registry candidates: " + (candidates == 0 ? "none" : candidates)); }
    private Optional<OreResource> resourceForDisplayName(String value) { return resourceBox.getItems().stream().filter(resource -> resource.displayName().equalsIgnoreCase(value.trim())).findFirst(); }
    private List<OreResource> presetResources() { return Arrays.stream(OrePreset.values()).map(preset -> new OreResource(preset.label(), preset.match(), preset.match(), false, 0)).toList(); }

    private void updateYFields() { boolean disabled = allYButton.isSelected() || surfaceSearchButton.isSelected() || rockSearchButton.isSelected() || prospectingSearchButton.isSelected(); yMinField.setDisable(disabled); yMaxField.setDisable(disabled); updateRockMode(); }
    private void updateRockMode() { rockYField.setDisable(!rockSearchButton.isSelected() || !rockAtYButton.isSelected()); }
    private void updateRadiusWarning() { boolean visible = radius1024Button.isSelected(); radiusWarningLabel.setVisible(visible); radiusWarningLabel.setManaged(visible); }
    private void updateSearchType() { updateYFields(); updateRockMode(); updateResourceStatus(); updateSurfaceResourceStatus(); }
    private void updateResourceMode() { if (multipleResourcesButton.isSelected()) { String match = resourceMatch(); resourceChecks.forEach((resource, check) -> check.setSelected(resource.match().equalsIgnoreCase(match))); } updateResourceStatus(); }
    private void updateResourceStatus() { if (!oreSearchButton.isSelected()) return; Optional<OreResource> selected = resourceForDisplayName(resourceBox.getEditor().getText()); resourceStatusLabel.setText(selected.isEmpty() ? "Registry match: custom input" : selected.get().registryVerified() ? "Registry match: verified (" + selected.get().registryMatchCount() + " block codes)" : "Registry match: not verified - using \"" + selected.get().match() + "\" as custom match"); }
    private void rebuildResourceChecklist() { resourceChecks.clear(); resourceColors.clear(); resourceChecklist.getChildren().clear(); for (int index = 0; index < discoveredResources.size(); index++) { OreResource resource = discoveredResources.get(index); CheckBox check = new CheckBox(resource.displayName()); Region color = new Region(); color.setPrefSize(12, 12); Color awt = OreOverlayPalette.colorFor(resource.match(), index); resourceColors.put(resource, awt); color.setStyle("-fx-background-color: rgb(" + awt.getRed() + "," + awt.getGreen() + "," + awt.getBlue() + ");"); check.setGraphic(color); resourceChecks.put(resource, check); resourceChecklist.getChildren().add(check); } }
}
