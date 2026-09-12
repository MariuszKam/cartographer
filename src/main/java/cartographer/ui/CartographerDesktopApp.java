package cartographer.ui;

import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.marker.MarkerStore;
import cartographer.model.BlockInfo;
import cartographer.model.ParsedChunk;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockYFilter;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.List;

public class CartographerDesktopApp extends Application {

    private final TextField saveField = new TextField();
    private final Button browseButton = new Button("Browse...");
    private final ComboBox<OreResource> resourceBox = new ComboBox<>();
    private final TextField yMinField = new TextField();
    private final TextField yMaxField = new TextField();
    private final RadioButton allYButton = new RadioButton("All Y");
    private final RadioButton customYButton = new RadioButton("Custom range");
    private final RadioButton radius128Button = new RadioButton("128");
    private final RadioButton radius256Button = new RadioButton("256");
    private final RadioButton radius512Button = new RadioButton("512");
    private final Button renderButton = new Button("Render");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Label resultLabel = new Label("Select a save and render an ore map.");
    private final ImageView imageView = new ImageView();
    private final ScrollPane preview = new ScrollPane(imageView);

    private RenderActualOreMapUseCase useCase;
    private ResourceCatalogService resourceCatalogService;

    @Override
    public void start(Stage stage) {
        VcdbsReader reader = createReader();
        useCase = createUseCase(reader);
        resourceCatalogService = new ResourceCatalogService(
                reader,
                new ResourceAnalyzer()
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
        resourceBox.setValue(presetResources().getFirst());
        resourceBox.setEditable(true);
        resourceBox.setConverter(new StringConverter<OreResource>() {
            @Override
            public String toString(OreResource resource) {
                return resource == null ? "" : resource.displayName();
            }

            @Override
            public OreResource fromString(String value) {
                return resourceForDisplayName(value).orElse(null);
            }
        });

        ToggleGroup radiusGroup = new ToggleGroup();
        radius128Button.setToggleGroup(radiusGroup);
        radius256Button.setToggleGroup(radiusGroup);
        radius512Button.setToggleGroup(radiusGroup);
        radius256Button.setSelected(true);

        ToggleGroup yGroup = new ToggleGroup();
        allYButton.setToggleGroup(yGroup);
        customYButton.setToggleGroup(yGroup);
        allYButton.setSelected(true);
        yMinField.setPromptText("min");
        yMaxField.setPromptText("max");
        allYButton.selectedProperty().addListener(
                (observable, oldValue, selected) -> updateYFields()
        );
        updateYFields();

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
        resultLabel.setWrapText(true);
    }

    private VBox buildControls() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("SAVE"), 0, 0);
        grid.add(saveField, 0, 1, 2, 1);
        grid.add(browseButton, 1, 1);

        grid.add(new Label("RESOURCE"), 0, 2);
        grid.add(resourceBox, 0, 3, 2, 1);

        grid.add(new Label("RADIUS"), 0, 4);
        HBox radiusBox = new HBox(8, radius128Button, radius256Button, radius512Button);
        grid.add(radiusBox, 0, 5, 2, 1);

        grid.add(new Label("Y FILTER"), 0, 6);
        grid.add(allYButton, 0, 7);
        grid.add(customYButton, 1, 7);
        grid.add(yMinField, 0, 8);
        grid.add(yMaxField, 1, 8);

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
            discoverResources(selected.toPath());
        }
    }

    private void discoverResources(Path savePath) {
        setDiscoveryBusy(true);
        statusLabel.setText("Loading resources...");

        Task<List<OreResource>> task = new Task<>() {
            @Override
            protected List<OreResource> call() {
                return resourceCatalogService.discover(savePath);
            }
        };
        task.setOnSucceeded(event -> {
            List<OreResource> discovered = task.getValue();
            resourceBox.getItems().setAll(
                    discovered.isEmpty()
                            ? presetResources()
                            : discovered
            );
            if (!resourceBox.getItems().isEmpty()) {
                resourceBox.setValue(resourceBox.getItems().getFirst());
            }
            statusLabel.setText(
                    discovered.isEmpty()
                            ? "No resource maps found; custom matches are available."
                            : "Loaded " + discovered.size() + " resources."
            );
            setDiscoveryBusy(false);
        });
        task.setOnFailed(event -> {
            resourceBox.getItems().setAll(presetResources());
            resourceBox.setValue(resourceBox.getItems().getFirst());
            showFailure(task.getException());
            setDiscoveryBusy(false);
        });

        Thread worker = new Thread(task, "cartographer-resource-discovery");
        worker.setDaemon(true);
        worker.start();
    }

    private void render() {
        try {
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

    private RenderActualOreMapRequest requestFromControls() {
        if (saveField.getText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        String match = resourceMatch();
        if (match.isBlank()) {
            throw new IllegalArgumentException("Enter an ore match.");
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
                Optional.empty()
        );
    }

    private void showResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        imageView.setImage(SwingFXUtils.toFXImage(result.image(), null));
        imageView.setFitWidth(Math.max(720, result.image().getWidth()));
        imageView.setFitHeight(Math.max(620, result.image().getHeight()));
        resultLabel.setText(result.actualOreMap()
                .map(map -> "Resource: " + request.oreMatch().orElseThrow()
                        + "\nRadius: " + request.radius()
                        + "\nY filter: " + map.yFilter().description()
                        + "\nMatching blocks: " + map.matchingBlocks()
                        + "\nHit columns: " + map.hitColumns()
                        + "\nFound Y: " + foundY(map))
                .orElse("Resource: " + request.oreMatch().orElseThrow()
                        + "\nRadius: " + request.radius()
                        + "\nY filter: " + request.yFilter().description()
                        + "\nNo matching blocks found"));
        statusLabel.setText("Rendered.");
        setBusy(false);
    }

    private String foundY(cartographer.scanner.ActualBlockMap map) {
        return map.cells().isEmpty()
                ? "none"
                : map.minMatchedY() + ".." + map.maxMatchedY();
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
        radius128Button.setDisable(busy);
        radius256Button.setDisable(busy);
        radius512Button.setDisable(busy);
        allYButton.setDisable(busy);
        customYButton.setDisable(busy);
        yMinField.setDisable(busy || allYButton.isSelected());
        yMaxField.setDisable(busy || allYButton.isSelected());
        progress.setVisible(busy);
    }

    private void setDiscoveryBusy(boolean busy) {
        renderButton.setDisable(busy);
        browseButton.setDisable(busy);
        saveField.setDisable(busy);
        resourceBox.setDisable(busy);
    }

    private void updateYFields() {
        boolean disabled = allYButton.isSelected();
        yMinField.setDisable(disabled);
        yMaxField.setDisable(disabled);
    }

    private int selectedRadius() {
        if (radius128Button.isSelected()) {
            return 128;
        }
        if (radius512Button.isSelected()) {
            return 512;
        }
        return 256;
    }

    private String resourceMatch() {
        String editor = resourceBox.getEditor().getText().trim();
        return resourceForDisplayName(editor)
                .map(OreResource::match)
                .orElse(editor);
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
                        preset.match()
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

    private RenderActualOreMapUseCase createUseCase(VcdbsReader reader) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderActualOreMapUseCase(
                reader,
                new WorldMetadataReader(),
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter()
        );
    }
}
