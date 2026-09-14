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
import cartographer.prospecting.SavedOreObservationProvider;
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
import cartographer.model.SurfaceBlock;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.workstation.WorldPanel;
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

    private TextField saveField;
    private Button browseButton;
    private RadioButton oreSearchButton;
    private RadioButton surfaceSearchButton;
    private RadioButton rockSearchButton;
    private RadioButton prospectingSearchButton;
    private ComboBox<OreResource> resourceBox;
    private ComboBox<SurfaceResourcePreset> surfaceResourceBox;
    private RadioButton singleResourceButton;
    private RadioButton multipleResourcesButton;
    private Button selectAllButton;
    private Button clearAllButton;
    private TextField yMinField;
    private TextField yMaxField;
    private RadioButton rockUpperButton;
    private RadioButton rockAtYButton;
    private TextField rockYField;
    private VBox rockLegendBox;
    private TextField prospectingResourceField;
    private VBox prospectingResultsBox;
    private RadioButton allYButton;
    private RadioButton customYButton;
    private RadioButton radius128Button;
    private RadioButton radius256Button;
    private RadioButton radius512Button;
    private RadioButton radius1024Button;
    private Button renderButton;
    private ProgressIndicator progress;
    private Label statusLabel;
    private Label resourceStatusLabel;
    private Label surfaceResourceStatusLabel;
    private Label playerStatusLabel;
    private Label resultLabel;
    private ImageView imageView;
    private ScrollPane preview;
    private WorkstationView workstation;
    private SearchPanel searchPanel;
    private WorldPanel worldPanel;
    private MapPanel mapPanel;

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
                new ResourceAnalyzer(),
                cartographer.prospecting.OreRockCompatibilityProvider.unknown(),
                new SavedOreObservationProvider(reader, metadataReader)
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

        workstation = new WorkstationView(
                () -> chooseSave(stage),
                this::render
        );
        worldPanel = workstation.worldPanel();
        searchPanel = workstation.searchPanel();
        mapPanel = workstation.mapPanel();
        saveField = worldPanel.saveField();
        browseButton = worldPanel.browseButton();
        oreSearchButton = searchPanel.oreSearchButton();
        surfaceSearchButton = searchPanel.surfaceSearchButton();
        rockSearchButton = searchPanel.rockSearchButton();
        prospectingSearchButton = searchPanel.prospectingSearchButton();
        resourceBox = searchPanel.resourceBox();
        surfaceResourceBox = searchPanel.surfaceResourceBox();
        singleResourceButton = searchPanel.singleResourceButton();
        multipleResourcesButton = searchPanel.multipleResourcesButton();
        selectAllButton = searchPanel.selectAllButton();
        clearAllButton = searchPanel.clearAllButton();
        yMinField = searchPanel.yMinField();
        yMaxField = searchPanel.yMaxField();
        rockUpperButton = searchPanel.rockUpperButton();
        rockAtYButton = searchPanel.rockAtYButton();
        rockYField = searchPanel.rockYField();
        rockLegendBox = searchPanel.rockLegendBox();
        prospectingResourceField = searchPanel.prospectingResourceField();
        prospectingResultsBox = searchPanel.prospectingResultsBox();
        allYButton = searchPanel.allYButton();
        customYButton = searchPanel.customYButton();
        radius128Button = searchPanel.radius128Button();
        radius256Button = searchPanel.radius256Button();
        radius512Button = searchPanel.radius512Button();
        radius1024Button = searchPanel.radius1024Button();
        renderButton = searchPanel.renderButton();
        progress = searchPanel.progress();
        statusLabel = searchPanel.statusLabel();
        resourceStatusLabel = searchPanel.resourceStatusLabel();
        surfaceResourceStatusLabel = searchPanel.surfaceResourceStatusLabel();
        resultLabel = searchPanel.resultLabel();
        playerStatusLabel = worldPanel.playerStatusLabel();
        imageView = mapPanel.imageView();
        preview = mapPanel.preview();
        Scene scene = new Scene(workstation.root(), 1180, 760);
        stage.setScene(scene);
        stage.show();
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
            searchPanel.setResources(discovered, loaded.registry());
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
            searchPanel.setDiscoveryFailure();
            discoveredResources = searchPanel.presetResources();
            loadedRegistry = Map.of();
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
                        preset.requiredTokens(),
                        preset.acceptedCodePrefixes()
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
        if (result.surfaceObjectScanUsed()) {
            resultText.append("\nExposed obsidian rock: ")
                    .append(result.exposedObsidianCount())
                    .append("\nLoose obsidian: ")
                    .append(result.looseObsidianCount())
                    .append("\nTotal surface observations: ")
                    .append(result.analysis().matchingBlockCount());
            resultText.append("\nRegistry variants: ")
                    .append(result.surfaceObjectRegistryVariants())
                    .append("\nSurface positions inspected: ")
                    .append(result.surfaceObjectPositionsInspected())
                    .append("\nUnavailable surface positions: ")
                    .append(result.surfaceObjectUnavailablePositions())
                    .append("\nObserved surface objects: ")
                    .append(result.surfaceObjectObservedTargets())
                    .append("\nNot observed: ")
                    .append(result.surfaceObjectNotObservedTargets())
                    .append("\nChunk outcomes: decoded=")
                    .append(result.surfaceObjectChunkStats().fullyDecodedChunks())
                    .append(", palette rejected=")
                    .append(result.surfaceObjectChunkStats().paletteRejectedChunks())
                    .append(", missing=")
                    .append(result.surfaceObjectChunkStats().uniquePositionsRequested()
                            - result.surfaceObjectChunkStats().rowsFound())
                    .append(", failed=")
                    .append(result.surfaceObjectChunkStats().failedChunks());
            if (result.surfaceObjectRegistryVariants() == 0) {
                resultText.append("\nNo block registry codes matched the surface resource families.");
            }
            int observationLimit = Math.min(20, result.analysis().matchingBlocks().size());
            if (observationLimit > 0) {
                resultText.append("\nObservations:");
                for (int index = 0; index < observationLimit; index++) {
                    var point = result.analysis().matchingBlocks().get(index);
                    resultText.append("\n  ")
                            .append(point.blockCode())
                            .append(" @ ")
                            .append(point.worldX())
                            .append(", ")
                            .append(point.y())
                            .append(", ")
                            .append(point.worldZ());
                }
            }
        }
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
                                    + actualOreText(assessment)
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

    private String actualOreText(ProspectingAssessment assessment) {
        return switch (assessment.candidate().evidence().actualOreObservation()) {
            case OBSERVED -> "observed";
            case NOT_OBSERVED -> "not observed";
            case UNAVAILABLE -> "unavailable";
        };
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

    private String resourceMatch() {
        String editor = resourceBox.getEditor().getText().trim();
        return resourceForDisplayName(editor)
                .map(OreResource::match)
                .orElse(editor);
    }

    private void updateResourceStatus() {
        searchPanel.updateResourceStatus();
    }

    private void updateSurfaceResourceStatus() {
        searchPanel.updateSurfaceResourceStatus();
    }

    private void updateSearchType() {
        updateYFields();
        updateRockMode();
        updateResourceStatus();
        updateSurfaceResourceStatus();
    }

    private Optional<SurfaceResourcePreset> surfacePresetFor(String value) {
        return searchPanel.surfacePresetFor(value);
    }

    private List<ActualOreOverlaySpec> selectedOverlays() {
        return searchPanel.selectedOverlays();
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
