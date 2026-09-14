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
import cartographer.prospecting.SavedOreObservationProvider;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderStyle;
import cartographer.render.UserMarkerRenderer;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.SurfaceResourceAnalyzer;
import cartographer.model.BlockInfo;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.ui.workstation.MapPanel;
import cartographer.ui.workstation.ResultInspectorPane;
import cartographer.ui.workstation.SearchPanel;
import cartographer.ui.workstation.WorkstationView;
import cartographer.ui.workstation.WorldPanel;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class CartographerDesktopApp extends Application {

    private WorkstationView workstation;
    private SearchPanel searchPanel;
    private WorldPanel worldPanel;
    private MapPanel mapPanel;
    private ResultInspectorPane resultInspector;

    private RenderActualOreMapUseCase useCase;
    private RenderSurfaceResourceMapUseCase surfaceUseCase;
    private RenderRockMapUseCase rockUseCase;
    private AnalyzeProspectingAreaUseCase prospectingUseCase;
    private VcdbsReader reader;
    private WorldMetadataReader metadataReader;
    private ResourceCatalogService resourceCatalogService;
    private PlayerPositionService playerPositionService;

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
        resultInspector = workstation.resultInspectorPane();
        Scene scene = new Scene(workstation.root(), 1180, 760);
        scene.getStylesheets().add(
                getClass().getResource("/cartographer/ui/cartographer-dark.css").toExternalForm()
        );
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
            worldPanel.setSavePath(selected.toPath().toString());
            workstation.setSavePath(selected.toPath());
            workstation.setStatus("");
            loadSaveData(selected.toPath());
        }
    }

    private void loadSaveData(Path savePath) {
        workstation.setDiscoveryBusy(true);
        workstation.setStatus("Loading resources and player position...");
        workstation.setPlayerLoaded(false);
        worldPanel.setPlayerStatus("Player: loading...");

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
            searchPanel.setResources(discovered, loaded.registry());
            worldPanel.setPlayerStatus(
                    loaded.player().map(this::formatPlayer).orElse("Player: unavailable")
            );
            workstation.setPlayerLoaded(loaded.player().isPresent());
            workstation.setStatus(
                    discovered.isEmpty()
                            ? "No resource maps found; custom matches are available."
                            : "Loaded " + discovered.size() + " resources."
            );
            workstation.setDiscoveryBusy(false);
        });
        task.setOnFailed(event -> {
            searchPanel.setDiscoveryFailure();
            worldPanel.setPlayerStatus("Player: unavailable");
            workstation.setPlayerLoaded(false);
            showFailure(task.getException());
            workstation.setDiscoveryBusy(false);
        });

        Thread worker = new Thread(task, "cartographer-resource-discovery");
        worker.setDaemon(true);
        worker.start();
    }

    private void render() {
        try {
            if (searchPanel.selectedMode() == SearchPanel.SearchMode.PROSPECTING) {
                analyzeProspectingArea();
                return;
            }
            if (searchPanel.selectedMode() == SearchPanel.SearchMode.ROCK) {
                renderRockMap();
                return;
            }
            if (searchPanel.selectedMode() == SearchPanel.SearchMode.SURFACE) {
                renderSurfaceResource();
                return;
            }
            RenderActualOreMapRequest request = requestFromControls();
            setBusy(true);
            workstation.setStatus("Rendering...");
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
        workstation.setStatus("Rendering observed rock geology...");
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
        if (worldPanel.savePathText().isBlank()) {
            showFailure(new IllegalArgumentException("Select a .vcdbs save."));
            return;
        }
        String resource = searchPanel.prospectingResourceText();
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of(worldPanel.savePathText()),
                Optional.empty(),
                searchPanel.selectedRadius(),
                resource.isBlank() ? Optional.empty() : Optional.of(resource)
        );
        setBusy(true);
        workstation.setStatus("Analyzing prospecting evidence...");
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
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        OptionalInt y = OptionalInt.empty();
        if (searchPanel.rockAtY()) {
            Integer value = parseOptionalInteger(searchPanel.rockYText(), "Rock Y");
            if (value == null) {
                throw new IllegalArgumentException("Enter a world Y for At Y mode.");
            }
            y = OptionalInt.of(value);
        }
        return new RenderRockMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.rockAtY() ? RockMapMode.AT_Y : RockMapMode.UPPER_ROCK,
                searchPanel.selectedRadius(),
                Optional.empty(),
                y,
                OptionalInt.empty(),
                OptionalInt.empty()
        );
    }

    private void renderSurfaceResource() {
        RenderSurfaceResourceMapRequest request = surfaceRequestFromControls();
        setBusy(true);
        workstation.setStatus("Rendering surface resource...");
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
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        String typed = searchPanel.surfaceResourceText();
        if (typed.isBlank()) {
            throw new IllegalArgumentException("Enter a surface resource match.");
        }
        SurfaceResourceMatch match = searchPanel.surfacePresetFor(typed)
                .map(preset -> new SurfaceResourceMatch(
                        preset.label(),
                        preset.requiredTokens(),
                        preset.acceptedCodePrefixes()
                ))
                .orElseGet(() -> new SurfaceResourceMatch(typed, List.of(typed)));
        return new RenderSurfaceResourceMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                match,
                Optional.empty()
        );
    }

    private RenderActualOreMapRequest requestFromControls() {
        if (worldPanel.savePathText().isBlank()) {
            throw new IllegalArgumentException("Select a .vcdbs save.");
        }
        List<ActualOreOverlaySpec> overlays = selectedOverlays();
        String match = overlays.isEmpty() ? "" : overlays.getFirst().match();
        if (match.isBlank()) {
            throw new IllegalArgumentException(
                            searchPanel.multipleResources()
                            ? "Select at least one resource."
                            : "Enter an ore match."
            );
        }
        Integer min = null;
        Integer max = null;
        if (searchPanel.customYEnabled()) {
            min = parseOptionalInteger(searchPanel.yMinText(), "Y minimum");
            max = parseOptionalInteger(searchPanel.yMaxText(), "Y maximum");
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("Y minimum must not exceed Y maximum.");
            }
        }
        return new RenderActualOreMapRequest(
                Path.of(worldPanel.savePathText()),
                searchPanel.selectedRadius(),
                1,
                RenderStyle.TOPOGRAPHIC,
                workstation.selectedRenderLayers(),
                Optional.of(match),
                new ActualBlockYFilter(min, max),
                Optional.empty(),
                overlays
        );
    }

    private void showResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        mapPanel.show(result.image());
        resultInspector.showOreResult(result, request);
        workstation.setStatus("Rendered.");
        setBusy(false);
    }

    private void showSurfaceResult(
            RenderSurfaceResourceMapResult result,
            RenderSurfaceResourceMapRequest request
    ) {
        mapPanel.show(result.image());
        resultInspector.showSurfaceResult(result, request);
        workstation.setStatus("Rendered.");
        setBusy(false);
    }

    private void showRockResult(
            RenderRockMapResult result,
            RenderRockMapRequest request
    ) {
        mapPanel.show(result.rendered().image());
        resultInspector.showRockResult(result, request);
        workstation.setStatus("Rock map rendered.");
        setBusy(false);
    }

    private void showProspectingResult(ProspectingAreaResult result) {
        resultInspector.showProspectingResult(result);
        workstation.setStatus("Prospecting analysis complete.");
        setBusy(false);
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
        workstation.setStatus("Error: " + conciseMessage(failure));
        resultInspector.showError(failure);
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
        workstation.setBusy(busy);
    }

    private List<ActualOreOverlaySpec> selectedOverlays() {
        return searchPanel.selectedOverlays();
    }

    private Integer parseOptionalInteger(String text, String label) {
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
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
