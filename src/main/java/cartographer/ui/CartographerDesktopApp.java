package cartographer.ui;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.RenderSurfaceResourceMapRequest;
import cartographer.application.RenderSurfaceResourceMapResult;
import cartographer.application.RenderSurfaceResourceMapUseCase;
import cartographer.application.SurfaceResourceMatch;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.ProspectingAreaRequest;
import cartographer.application.ProspectingAreaResult;
import cartographer.geology.rock.RockMapMode;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.OreOverlayPalette;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.RockLegendEntry;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.model.BlockInfo;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.awt.Color;
import java.util.OptionalInt;

public class CartographerDesktopApp extends Application {

    private final TextField saveField = new TextField();
    private final Button browseButton = new Button("Browse...");
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
    private final Label radiusWarningLabel = new Label(
            "Large radius: rendering may take longer and use substantially more memory."
    );
    private final Button renderButton = new Button("Render");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Label resourceStatusLabel = new Label();
    private final Label surfaceResourceStatusLabel = new Label();
    private final Label playerStatusLabel = new Label("Player: not loaded");
    private final Label resultLabel = new Label("Select a save and render an ore map.");
    private final ImageView imageView = new ImageView();
    private final ScrollPane preview = new ScrollPane(imageView);

    private RenderActualOreMapUseCase useCase;
    private RenderSurfaceResourceMapUseCase surfaceUseCase;
    private RenderRockMapUseCase rockUseCase;
    private AnalyzeProspectingAreaUseCase prospectingUseCase;
    private VcdbsReader reader;
    private WorldMetadataReader metadataReader;
    private ResourceCatalogService resourceCatalogService;
    private PlayerPositionService playerPositionService;
    private List<OreResource> discoveredResources = List.of();
    private Map<Integer, BlockInfo> loadedRegistry = Map.of();

    @Override
    public void start(Stage stage) {
        reader = createReader();
        metadataReader = new WorldMetadataReader();
        useCase = createUseCase(reader, metadataReader);
        surfaceUseCase = createSurfaceUseCase(reader, metadataReader);
        rockUseCase = new RenderRockMapUseCase(
                reader,
                metadataReader,
                new cartographer.render.RockMapRenderer()
        );
        prospectingUseCase = new AnalyzeProspectingAreaUseCase(
                reader,
                rockUseCase,
                new ResourceAnalyzer()
        );
        resourceCatalogService = new ResourceCatalogService(
                reader,
                new ResourceAnalyzer()
        );
        playerPositionService = new PlayerPositionService(
                reader,
                metadataReader
        );
        stage.setTitle("VS Cartographer");

        configureControls(stage);
        VBox controls = buildControls();
        BorderPane root = new BorderPane();
        root.setLeft(controls);
        root.setCenter(preview);
        BorderPane.setMargin(controls, new Insets(12));
        BorderPane.setMargin(preview, new Insets(12, 12, 12, 0));

        Scene scene = new Scene(root, 1180, 760);
        stage.setScene(scene);
        stage.show();
    }

    private void configureControls(Stage stage) {
        saveField.setEditable(false);
        saveField.setPromptText("Select a .vcdbs save");

        resourceBox.getItems().setAll(presetResources());
        discoveredResources = presetResources();
        resourceBox.setValue(presetResources().getFirst());
        resourceBox.setEditable(true);
        resourceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(OreResource resource) {
                return resource == null ? "" : resource.displayName();
            }

            @Override
            public OreResource fromString(String value) {
                return resourceForDisplayName(value).orElse(null);
            }
        });
        resourceBox.valueProperty().addListener(
                (observable, oldValue, selected) -> updateResourceStatus()
        );
        resourceBox.getEditor().textProperty().addListener(
                (observable, oldValue, typed) -> updateResourceStatus()
        );
        surfaceResourceBox.getItems().setAll(List.of(SurfaceResourcePreset.values()));
        surfaceResourceBox.setEditable(true);
        surfaceResourceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SurfaceResourcePreset preset) {
                return preset == null ? "" : preset.label();
            }

            @Override
            public SurfaceResourcePreset fromString(String value) {
                return surfacePresetFor(value).orElse(null);
            }
        });
        surfaceResourceBox.setValue(SurfaceResourcePreset.FIRE_CLAY);
        surfaceResourceBox.getEditor().textProperty().addListener(
                (observable, oldValue, typed) -> updateSurfaceResourceStatus()
        );
        ToggleGroup searchType = new ToggleGroup();
        oreSearchButton.setToggleGroup(searchType);
        surfaceSearchButton.setToggleGroup(searchType);
        rockSearchButton.setToggleGroup(searchType);
        prospectingSearchButton.setToggleGroup(searchType);
        oreSearchButton.setSelected(true);
        searchType.selectedToggleProperty().addListener(
                (observable, oldValue, selected) -> updateSearchType()
        );
        ToggleGroup resourceMode = new ToggleGroup();
        singleResourceButton.setToggleGroup(resourceMode);
        multipleResourcesButton.setToggleGroup(resourceMode);
        singleResourceButton.setSelected(true);
        resourceMode.selectedToggleProperty().addListener(
                (observable, oldValue, selected) -> updateResourceMode()
        );
        selectAllButton.setOnAction(event ->
                resourceChecks.values().forEach(check -> check.setSelected(true)));
        clearAllButton.setOnAction(event ->
                resourceChecks.values().forEach(check -> check.setSelected(false)));

        ToggleGroup radiusGroup = new ToggleGroup();
        radius128Button.setToggleGroup(radiusGroup);
        radius256Button.setToggleGroup(radiusGroup);
        radius512Button.setToggleGroup(radiusGroup);
        radius1024Button.setToggleGroup(radiusGroup);
        radius256Button.setSelected(true);
        radius1024Button.selectedProperty().addListener(
                (observable, oldValue, selected) -> updateRadiusWarning()
        );
        radiusWarningLabel.setVisible(false);
        radiusWarningLabel.setManaged(false);

        ToggleGroup yGroup = new ToggleGroup();
        allYButton.setToggleGroup(yGroup);
        customYButton.setToggleGroup(yGroup);
        allYButton.setSelected(true);
        yMinField.setPromptText("min");
        yMaxField.setPromptText("max");
        rockYField.setPromptText("world Y");
        ToggleGroup rockMode = new ToggleGroup();
        rockUpperButton.setToggleGroup(rockMode);
        rockAtYButton.setToggleGroup(rockMode);
        rockUpperButton.setSelected(true);
        rockAtYButton.selectedProperty().addListener(
                (observable, oldValue, selected) -> updateRockMode()
        );
        allYButton.selectedProperty().addListener(
                (observable, oldValue, selected) -> updateYFields()
        );
        updateYFields();
        updateRockMode();

        browseButton.setOnAction(event -> chooseSave(stage));
        renderButton.setOnAction(event -> render());
        preview.setPannable(true);
        preview.setFitToWidth(false);
        preview.setFitToHeight(false);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        progress.setVisible(false);
        progress.setPrefSize(28, 28);
        statusLabel.setWrapText(true);
        resourceStatusLabel.setWrapText(true);
        playerStatusLabel.setWrapText(true);
        resultLabel.setWrapText(true);
        updateResourceStatus();
        updateSurfaceResourceStatus();
    }

    private VBox buildControls() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("SAVE"), 0, 0);
        grid.add(saveField, 0, 1, 2, 1);
        grid.add(browseButton, 1, 1);
        grid.add(new Label("PLAYER"), 0, 2);
        grid.add(playerStatusLabel, 0, 3, 2, 1);

        grid.add(new Label("SEARCH TYPE"), 0, 4);
        grid.add(new FlowPane(
                8, 4,
                oreSearchButton,
                surfaceSearchButton,
                rockSearchButton,
                prospectingSearchButton
        ), 0, 5, 2, 1);
        grid.add(new Label("RESOURCE"), 0, 6);
        HBox resourceMode = new HBox(8, singleResourceButton, multipleResourcesButton);
        grid.add(resourceMode, 0, 7, 2, 1);
        VBox singleResourcePanel = new VBox(4, resourceBox, resourceStatusLabel);
        grid.add(singleResourcePanel, 0, 8, 2, 1);
        HBox multiActions = new HBox(6, selectAllButton, clearAllButton);
        resourceChecklistScroll.setFitToWidth(true);
        resourceChecklistScroll.setPrefViewportHeight(130);
        VBox multiResourcePanel = new VBox(4, multiActions, resourceChecklistScroll);
        grid.add(multiResourcePanel, 0, 9, 2, 1);
        singleResourcePanel.managedProperty().bind(singleResourcePanel.visibleProperty());
        multiResourcePanel.managedProperty().bind(multiResourcePanel.visibleProperty());
        resourceMode.visibleProperty().bind(oreSearchButton.selectedProperty());
        resourceMode.managedProperty().bind(resourceMode.visibleProperty());
        singleResourcePanel.visibleProperty().bind(
                oreSearchButton.selectedProperty().and(singleResourceButton.selectedProperty())
        );
        multiResourcePanel.visibleProperty().bind(
                oreSearchButton.selectedProperty().and(multipleResourcesButton.selectedProperty())
        );

        VBox surfacePanel = new VBox(
                4,
                surfaceResourceBox,
                surfaceResourceStatusLabel
        );
        grid.add(surfacePanel, 0, 7, 2, 3);
        surfacePanel.visibleProperty().bind(surfaceSearchButton.selectedProperty());
        surfacePanel.managedProperty().bind(surfacePanel.visibleProperty());

        VBox rockPanel = new VBox(
                4,
                new HBox(8, rockUpperButton, rockAtYButton),
                rockYField,
                rockLegendScroll
        );
        rockLegendScroll.setFitToWidth(true);
        rockLegendScroll.setPrefViewportHeight(130);
        grid.add(rockPanel, 0, 7, 2, 3);
        rockPanel.visibleProperty().bind(rockSearchButton.selectedProperty());
        rockPanel.managedProperty().bind(rockPanel.visibleProperty());

        VBox prospectingPanel = new VBox(
                4,
                prospectingResourceField,
                prospectingResultsBox
        );
        prospectingResourceField.setPromptText("Resource name, or blank for all");
        grid.add(prospectingPanel, 0, 7, 2, 3);
        prospectingPanel.visibleProperty().bind(prospectingSearchButton.selectedProperty());
        prospectingPanel.managedProperty().bind(prospectingPanel.visibleProperty());

        grid.add(new Label("RADIUS"), 0, 10);
        FlowPane radiusBox = new FlowPane(
                8, 4, radius128Button, radius256Button,
                radius512Button, radius1024Button
        );
        grid.add(radiusBox, 0, 11, 2, 1);
        grid.add(radiusWarningLabel, 0, 12, 2, 1);

        grid.add(new Label("Y FILTER"), 0, 13);
        grid.add(allYButton, 0, 14);
        grid.add(customYButton, 1, 14);
        grid.add(yMinField, 0, 15);
        grid.add(yMaxField, 1, 15);

        VBox box = new VBox(
                12,
                grid,
                new Separator(),
                new HBox(8, renderButton, progress),
                statusLabel,
                resultLabel
        );
        box.setPrefWidth(280);
        return box;
    }

    private void chooseSave(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Vintage Story save");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Vintage Story saves (*.vcdbs)", "*.vcdbs")
        );
        Path saves = Path.of(
                System.getenv().getOrDefault("APPDATA", ""),
                "VintagestoryData",
                "Saves"
        );
        if (Files.isDirectory(saves)) {
            chooser.setInitialDirectory(saves.toFile());
        }
        var selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            saveField.setText(selected.toPath().toString());
            statusLabel.setText("");
            loadSaveData(selected.toPath());
        }
    }

    private void loadSaveData(Path savePath) {
        setDiscoveryBusy(true);
        statusLabel.setText("Loading resources and player position...");
        playerStatusLabel.setText("Player: loading...");

        Task<SaveLoadResult> task = new Task<>() {
            @Override
            protected SaveLoadResult call() {
                List<OreResource> resources = resourceCatalogService.discover(savePath);
                Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath);
                try {
                    return new SaveLoadResult(
                            resources,
                            Optional.of(playerPositionService.load(savePath)),
                            registry
                    );
                } catch (RuntimeException exception) {
                    return new SaveLoadResult(resources, Optional.empty(), registry);
                }
            }
        };
        task.setOnSucceeded(event -> {
            SaveLoadResult loaded = task.getValue();
            List<OreResource> discovered = loaded.resources();
            discoveredResources = discovered;
            loadedRegistry = loaded.registry();
            rebuildResourceChecklist();
            resourceBox.getItems().setAll(
                    discovered.isEmpty()
                            ? presetResources()
                            : discovered
            );
            if (!resourceBox.getItems().isEmpty()) {
                resourceBox.setValue(resourceBox.getItems().getFirst());
            }
            updateResourceStatus();
            updateSurfaceResourceStatus();
            playerStatusLabel.setText(
                    loaded.player().map(this::formatPlayer).orElse("Player: unavailable")
            );
            statusLabel.setText(
                    discovered.isEmpty()
                            ? "No resource maps found; custom matches are available."
                            : "Loaded " + discovered.size() + " resources."
            );
            setDiscoveryBusy(false);
        });
        task.setOnFailed(event -> {
            resourceBox.getItems().setAll(presetResources());
            discoveredResources = presetResources();
            loadedRegistry = Map.of();
            rebuildResourceChecklist();
            resourceBox.setValue(resourceBox.getItems().getFirst());
            resourceStatusLabel.setText("Registry match: unavailable");
            playerStatusLabel.setText("Player: unavailable");
            showFailure(task.getException());
            setDiscoveryBusy(false);
        });

        Thread worker = new Thread(task, "cartographer-resource-discovery");
        worker.setDaemon(true);
        worker.start();
    }

    private void render() {
        try {
            if (prospectingSearchButton.isSelected()) {
                analyzeProspectingArea();
                return;
            }
            if (rockSearchButton.isSelected()) {
                renderRockMap();
                return;
            }
            if (surfaceSearchButton.isSelected()) {
                renderSurfaceResource();
                return;
            }
            RenderActualOreMapRequest request = requestFromControls();
            setBusy(true);
            statusLabel.setText("Rendering...");
            Task<RenderActualOreMapResult> task = new Task<>() {
                @Override
                protected RenderActualOreMapResult call() {
                    return useCase.execute(request);
                }
            };
            task.setOnSucceeded(event -> showResult(task.getValue(), request));
            task.setOnFailed(event -> showFailure(task.getException()));
            Thread worker = new Thread(task, "cartographer-ore-map-render");
            worker.setDaemon(true);
            worker.start();
        } catch (RuntimeException exception) {
            showFailure(exception);
        }
    }

    private void renderRockMap() {
        RenderRockMapRequest request = rockRequestFromControls();
        setBusy(true);
        statusLabel.setText("Rendering observed rock geology...");
        Task<RenderRockMapResult> task = new Task<>() {
            @Override
            protected RenderRockMapResult call() {
                return rockUseCase.execute(request);
            }
        };
        task.setOnSucceeded(event -> showRockResult(task.getValue(), request));
        task.setOnFailed(event -> showFailure(task.getException()));
        Thread worker = new Thread(task, "cartographer-rock-map-render");
        worker.setDaemon(true);
        worker.start();
    }

    private void analyzeProspectingArea() {
        if (saveField.getText().isBlank()) {
            showFailure(new IllegalArgumentException("Select a .vcdbs save."));
            return;
        }
        String resource = prospectingResourceField.getText().trim();
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of(saveField.getText()),
                Optional.empty(),
                selectedRadius(),
                resource.isBlank() ? Optional.empty() : Optional.of(resource)
        );
        setBusy(true);
        statusLabel.setText("Analyzing prospecting evidence...");
        Task<ProspectingAreaResult> task = new Task<>() {
            @Override
            protected ProspectingAreaResult call() {
                return prospectingUseCase.execute(request);
            }
        };
        task.setOnSucceeded(event -> showProspectingResult(task.getValue()));
        task.setOnFailed(event -> showFailure(task.getException()));
        Thread worker = new Thread(task, "cartographer-prospecting-analysis");
        worker.setDaemon(true);
        worker.start();
    }

    private RenderRockMapRequest rockRequestFromControls() {
        if (saveField.getText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        OptionalInt y = OptionalInt.empty();
        if (rockAtYButton.isSelected()) {
            Integer value = parseOptionalInteger(rockYField, "Rock Y");
            if (value == null) {
                throw new IllegalArgumentException("Enter a world Y for At Y mode.");
            }
            y = OptionalInt.of(value);
        }
        return new RenderRockMapRequest(
                Path.of(saveField.getText()),
                rockAtYButton.isSelected() ? RockMapMode.AT_Y : RockMapMode.UPPER_ROCK,
                selectedRadius(),
                Optional.empty(),
                y,
                OptionalInt.empty(),
                OptionalInt.empty()
        );
    }

    private void renderSurfaceResource() {
        RenderSurfaceResourceMapRequest request = surfaceRequestFromControls();
        setBusy(true);
        statusLabel.setText("Rendering surface resource...");
        Task<RenderSurfaceResourceMapResult> task = new Task<>() {
            @Override
            protected RenderSurfaceResourceMapResult call() {
                return surfaceUseCase.execute(request);
            }
        };
        task.setOnSucceeded(event -> showSurfaceResult(task.getValue(), request));
        task.setOnFailed(event -> showFailure(task.getException()));
        Thread worker = new Thread(task, "cartographer-surface-resource-render");
        worker.setDaemon(true);
        worker.start();
    }

    private RenderSurfaceResourceMapRequest surfaceRequestFromControls() {
        if (saveField.getText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        String typed = surfaceResourceBox.getEditor().getText().trim();
        if (typed.isBlank()) {
            throw new IllegalArgumentException("Enter a surface resource match.");
        }
        SurfaceResourceMatch match = surfacePresetFor(typed)
                .map(preset -> new SurfaceResourceMatch(
                        preset.label(),
                        preset.requiredTokens()
                ))
                .orElseGet(() -> new SurfaceResourceMatch(typed, List.of(typed)));
        return new RenderSurfaceResourceMapRequest(
                Path.of(saveField.getText()),
                selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
                match,
                Optional.empty()
        );
    }

    private RenderActualOreMapRequest requestFromControls() {
        if (saveField.getText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        List<ActualOreOverlaySpec> overlays = selectedOverlays();
        String match = overlays.isEmpty() ? "" : overlays.getFirst().match();
        if (match.isBlank()) {
            throw new IllegalArgumentException(
                    multipleResourcesButton.isSelected()
                            ? "Select at least one resource."
                            : "Enter an ore match."
            );
        }
        Integer min = null;
        Integer max = null;
        if (customYButton.isSelected()) {
            min = parseOptionalInteger(yMinField, "Y minimum");
            max = parseOptionalInteger(yMaxField, "Y maximum");
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("Y minimum must not exceed Y maximum.");
            }
        }
        return new RenderActualOreMapRequest(
                Path.of(saveField.getText()),
                selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
                Optional.of(match),
                new ActualBlockYFilter(min, max),
                Optional.empty(),
                overlays
        );
    }

    private void showResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        imageView.setImage(SwingFXUtils.toFXImage(result.image(), null));
        imageView.setFitWidth(Math.max(720, result.image().getWidth()));
        imageView.setFitHeight(Math.max(620, result.image().getHeight()));
        StringBuilder resultText = new StringBuilder()
                .append("Resources: ")
                .append(result.actualOreOverlays().size())
                .append("\nRadius: ")
                .append(request.radius())
                .append("\nY filter: ")
                .append(request.yFilter().description());
        long total = 0;
        for (var overlay : result.actualOreOverlays()) {
            var map = overlay.map();
            total += map.matchingBlocks();
            resultText.append("\n\n")
                    .append(overlay.spec().displayName())
                    .append("\n  Blocks: ")
                    .append(map.matchingBlocks())
                    .append("\n  Columns: ")
                    .append(map.hitColumns())
                    .append("\n  Y: ")
                    .append(foundY(map));
        }
        resultText.append("\n\nTotal matching blocks: ").append(total);
        resultLabel.setText(resultText.toString());
        statusLabel.setText("Rendered.");
        setBusy(false);
    }

    private void showSurfaceResult(
            RenderSurfaceResourceMapResult result,
            RenderSurfaceResourceMapRequest request
    ) {
        imageView.setImage(SwingFXUtils.toFXImage(result.image(), null));
        imageView.setFitWidth(Math.max(720, result.image().getWidth()));
        imageView.setFitHeight(Math.max(620, result.image().getHeight()));
        StringBuilder resultText = new StringBuilder()
                .append("Surface resource: ")
                .append(request.match().displayName())
                .append("\nRadius: ")
                .append(request.radius())
                .append("\nMatching surface blocks: ")
                .append(result.analysis().matchingBlockCount())
                .append("\nConnected deposits: ")
                .append(result.analysis().depositCount());
        int limit = Math.min(5, result.analysis().deposits().size());
        if (limit > 0) {
            resultText.append("\n\nLargest deposits:");
            for (int index = 0; index < limit; index++) {
                var deposit = result.analysis().deposits().get(index);
                resultText.append("\n")
                        .append(index + 1)
                        .append(". blocks=")
                        .append(deposit.blockCount())
                        .append(" Y=")
                        .append(deposit.minY())
                        .append("..")
                        .append(deposit.maxY());
            }
        }
        resultLabel.setText(resultText.toString());
        statusLabel.setText("Rendered.");
        setBusy(false);
    }

    private void showRockResult(
            RenderRockMapResult result,
            RenderRockMapRequest request
    ) {
        imageView.setImage(SwingFXUtils.toFXImage(result.rendered().image(), null));
        imageView.setFitWidth(Math.max(720, result.rendered().image().getWidth()));
        imageView.setFitHeight(Math.max(620, result.rendered().image().getHeight()));
        rockLegendBox.getChildren().clear();
        for (RockLegendEntry entry : result.rendered().legend()) {
            Region swatch = new Region();
            swatch.setPrefSize(12, 12);
            int rgb = entry.argb();
            swatch.setStyle(
                    "-fx-background-color: rgb("
                            + ((rgb >> 16) & 0xff) + ","
                            + ((rgb >> 8) & 0xff) + ","
                            + (rgb & 0xff) + ");"
            );
            Label label = new Label(
                    entry.rock().code()
                            + " (" + entry.observedCellCount()
                            + ", " + String.format(
                                    java.util.Locale.ROOT,
                                    "%.2f%%",
                                    entry.observedPercentage()
                            ) + ")"
            );
            rockLegendBox.getChildren().add(new HBox(6, swatch, label));
        }
        StringBuilder text = new StringBuilder("Observed saved geology")
                .append("\nMode: ").append(request.mode())
                .append("\nRadius: ").append(request.radius());
        if (request.mode() == RockMapMode.AT_Y) {
            text.append("\nY: ").append(request.y().orElseThrow());
        } else {
            text.append("\nY range: ")
                    .append(result.minY()).append("..")
                    .append(result.maxYExclusive()).append(" (exclusive)");
        }
        text.append("\nRecognized rock types: ").append(result.catalog().rocks().size())
                .append("\nObserved: ").append(result.rendered().observedCount())
                .append("\nNo rock: ").append(result.rendered().noRockCount())
                .append("\nUnavailable: ").append(result.rendered().unavailableCount());
        resultLabel.setText(text.toString());
        statusLabel.setText("Rock map rendered.");
        setBusy(false);
    }

    private void showProspectingResult(ProspectingAreaResult result) {
        prospectingResultsBox.getChildren().clear();
        for (ProspectingAssessment assessment : result.assessments()) {
            prospectingResultsBox.getChildren().add(
                    new Label(
                            assessment.candidate().resourceKey()
                                    + " - " + assessment.rank()
                                    + " | signal: " + signalText(assessment)
                                    + " | geology: "
                                    + assessment.candidate().evidence().geologyState()
                                    + " | compatibility: "
                                    + assessment.compatibility()
                                    + " | actual ore: "
                                    + (assessment.candidate().evidence().actualOreObserved()
                                    ? "observed" : "not observed")
                                    + "\n  " + String.join(
                                    "; ",
                                    assessment.reasons()
                            )
                    )
            );
        }
        resultLabel.setText(
                "Prospecting evidence\n"
                        + "Observed saved geology and relative worldgen signals\n"
                        + "Candidates: " + result.assessments().size()
        );
        statusLabel.setText("Prospecting analysis complete.");
        setBusy(false);
    }

    private String signalText(ProspectingAssessment assessment) {
        return assessment.candidate().evidence().worldgenSignal().isPresent()
                ? String.format(
                        java.util.Locale.ROOT,
                        "%.3f relative",
                        assessment.candidate().evidence().worldgenSignal().getAsDouble()
                )
                : "unavailable";
    }

    private String foundY(cartographer.scanner.ActualBlockMap map) {
        return map.cells().isEmpty()
                ? "none"
                : map.minMatchedY() + ".." + map.maxMatchedY();
    }

    private String formatPlayer(PlayerPositionView player) {
        return String.format(
                java.util.Locale.ROOT,
                "X: %.1f%nY: %.1f%nZ: %.1f%nChunk: %d, %d",
                player.x(),
                player.y(),
                player.z(),
                player.chunkX(),
                player.chunkZ()
        );
    }

    private void showFailure(Throwable failure) {
        statusLabel.setText("Error: " + conciseMessage(failure));
        setBusy(false);
    }

    private String conciseMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void setBusy(boolean busy) {
        renderButton.setDisable(busy);
        browseButton.setDisable(busy);
        saveField.setDisable(busy);
        resourceBox.setDisable(busy);
        surfaceResourceBox.setDisable(busy);
        oreSearchButton.setDisable(busy);
        surfaceSearchButton.setDisable(busy);
        rockSearchButton.setDisable(busy);
        prospectingSearchButton.setDisable(busy);
        rockUpperButton.setDisable(busy);
        rockAtYButton.setDisable(busy);
        rockYField.setDisable(busy || !rockAtYButton.isSelected() || !rockSearchButton.isSelected());
        prospectingResourceField.setDisable(busy);
        singleResourceButton.setDisable(busy);
        multipleResourcesButton.setDisable(busy);
        selectAllButton.setDisable(busy);
        clearAllButton.setDisable(busy);
        radius128Button.setDisable(busy);
        radius256Button.setDisable(busy);
        radius512Button.setDisable(busy);
        radius1024Button.setDisable(busy);
        allYButton.setDisable(busy);
        customYButton.setDisable(busy);
        yMinField.setDisable(busy || allYButton.isSelected() || surfaceSearchButton.isSelected());
        yMaxField.setDisable(busy || allYButton.isSelected() || surfaceSearchButton.isSelected());
        progress.setVisible(busy);
    }

    private void setDiscoveryBusy(boolean busy) {
        renderButton.setDisable(busy);
        browseButton.setDisable(busy);
        saveField.setDisable(busy);
        resourceBox.setDisable(busy);
        surfaceResourceBox.setDisable(busy);
        oreSearchButton.setDisable(busy);
        surfaceSearchButton.setDisable(busy);
        rockSearchButton.setDisable(busy);
        prospectingSearchButton.setDisable(busy);
        prospectingResourceField.setDisable(busy);
        singleResourceButton.setDisable(busy);
        multipleResourcesButton.setDisable(busy);
        selectAllButton.setDisable(busy);
        clearAllButton.setDisable(busy);
    }

    private void updateYFields() {
        boolean disabled = allYButton.isSelected()
                || surfaceSearchButton.isSelected()
                || rockSearchButton.isSelected()
                || prospectingSearchButton.isSelected();
        yMinField.setDisable(disabled);
        yMaxField.setDisable(disabled);
        updateRockMode();
    }

    private void updateRockMode() {
        rockYField.setDisable(
                !rockSearchButton.isSelected() || !rockAtYButton.isSelected()
        );
    }

    private int selectedRadius() {
        if (radius128Button.isSelected()) {
            return 128;
        }
        if (radius512Button.isSelected()) {
            return 512;
        }
        if (radius1024Button.isSelected()) {
            return 1024;
        }
        return 256;
    }

    private void updateRadiusWarning() {
        boolean visible = radius1024Button.isSelected();
        radiusWarningLabel.setVisible(visible);
        radiusWarningLabel.setManaged(visible);
    }

    private String resourceMatch() {
        String editor = resourceBox.getEditor().getText().trim();
        return resourceForDisplayName(editor)
                .map(OreResource::match)
                .orElse(editor);
    }

    private void updateResourceStatus() {
        if (!oreSearchButton.isSelected()) {
            return;
        }
        String editor = resourceBox.getEditor().getText().trim();
        Optional<OreResource> selected = resourceBox.getItems().stream()
                .filter(resource -> resource.displayName().equalsIgnoreCase(editor))
                .findFirst();

        if (selected.isEmpty()) {
            resourceStatusLabel.setText("Registry match: custom input");
            return;
        }

        OreResource resource = selected.orElseThrow();
        resourceStatusLabel.setText(
                resource.registryVerified()
                        ? "Registry match: verified ("
                                + resource.registryMatchCount()
                                + " block codes)"
                        : "Registry match: not verified - using \""
                                + resource.match()
                                + "\" as custom match"
        );
    }

    private void updateSurfaceResourceStatus() {
        if (!surfaceSearchButton.isSelected()) {
            return;
        }
        String typed = surfaceResourceBox.getEditor().getText().trim();
        Optional<SurfaceResourcePreset> preset = surfacePresetFor(typed);
        if (preset.isEmpty()) {
            surfaceResourceStatusLabel.setText("Registry candidates: custom input");
            return;
        }
        long candidates = loadedRegistry.values().stream()
                .filter(block -> block.code() != null)
                .filter(block -> preset.get().requiredTokens().stream()
                        .allMatch(token -> block.code().toLowerCase(java.util.Locale.ROOT).contains(token)))
                .count();
        surfaceResourceStatusLabel.setText(
                "Registry candidates: " + (candidates == 0 ? "none" : candidates)
        );
    }

    private void updateSearchType() {
        updateYFields();
        updateRockMode();
        updateResourceStatus();
        updateSurfaceResourceStatus();
    }

    private Optional<SurfaceResourcePreset> surfacePresetFor(String value) {
        return java.util.Arrays.stream(SurfaceResourcePreset.values())
                .filter(preset -> preset.label().equalsIgnoreCase(value.trim()))
                .findFirst();
    }

    private void updateResourceMode() {
        if (multipleResourcesButton.isSelected()) {
            String currentMatch = resourceMatch();
            resourceChecks.forEach((resource, check) ->
                    check.setSelected(resource.match().equalsIgnoreCase(currentMatch)));
        }
        updateResourceStatus();
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

    private List<ActualOreOverlaySpec> selectedOverlays() {
        if (singleResourceButton.isSelected()) {
            String match = resourceMatch();
            if (match.isBlank()) {
                return List.of();
            }
            OreResource selected = resourceForDisplayName(
                    resourceBox.getEditor().getText().trim()
            ).orElse(null);
            String displayName = selected == null ? match : selected.displayName();
            Color color = selected == null
                    ? colorForCustomMatch(match)
                    : resourceColors.getOrDefault(
                            selected,
                            colorForCustomMatch(match)
                    );
            return List.of(
                    new ActualOreOverlaySpec(
                            displayName,
                            match,
                            color,
                            selected == null
                                    ? ActualBlockMatchMode.GENERIC_SUBSTRING
                                    : selected.registryVerified()
                                    ? ActualBlockMatchMode.ORE_CODE
                                    : ActualBlockMatchMode.GENERIC_SUBSTRING
                    )
            );
        }

        List<ActualOreOverlaySpec> result = new java.util.ArrayList<>();
        int colorIndex = 0;
        for (OreResource resource : discoveredResources) {
            CheckBox check = resourceChecks.get(resource);
            if (check == null || !check.isSelected()) {
                continue;
            }
            result.add(
                    new ActualOreOverlaySpec(
                            resource.displayName(),
                            resource.match(),
                            resourceColors.getOrDefault(
                                    resource,
                                    OreOverlayPalette.colorFor(
                                            resource.match(),
                                            colorIndex
                                    )
                            ),
                            resource.registryVerified()
                                    ? ActualBlockMatchMode.ORE_CODE
                                    : ActualBlockMatchMode.GENERIC_SUBSTRING
                    )
            );
            colorIndex++;
        }
        return List.copyOf(result);
    }

    private Color colorForCustomMatch(String match) {
        return OreOverlayPalette.colorFor(match, 0);
    }

    private Optional<OreResource> resourceForDisplayName(String value) {
        return resourceBox.getItems().stream()
                .filter(resource -> resource.displayName().equalsIgnoreCase(value.trim()))
                .findFirst();
    }

    private List<OreResource> presetResources() {
        return java.util.Arrays.stream(OrePreset.values())
                .map(preset -> new OreResource(
                        preset.label(),
                        preset.match(),
                        preset.match(),
                        false,
                        0
                ))
                .toList();
    }

    private Integer parseOptionalInteger(TextField field, String label) {
        if (field.getText().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
    }

    private VcdbsReader createReader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private RenderActualOreMapUseCase createUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter()
        );
    }

    private RenderSurfaceResourceMapUseCase createSurfaceUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderSurfaceResourceMapUseCase(
                reader,
                metadataReader,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new cartographer.scanner.SurfaceScanner(),
                new SurfaceResourceAnalyzer(),
                new SurfaceResourceOverlayRenderer()
        );
    }

    private record SaveLoadResult(
            List<OreResource> resources,
            Optional<PlayerPositionView> player,
            Map<Integer, BlockInfo> registry
    ) {
    }
}
