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
import cartographer.scanner.ActualBlockYFilter;
import cartographer.ui.workstation.MapPanel;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class CartographerDesktopApp extends Application {

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
            worldPanel.setSavePath(selected.toPath().toString());
            searchPanel.setStatus("");
            loadSaveData(selected.toPath());
        }
    }

    private void loadSaveData(Path savePath) {
        searchPanel.setDiscoveryBusy(true);
        worldPanel.setBusy(true);
        searchPanel.setStatus("Loading resources and player position...");
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
            searchPanel.setStatus(
                    discovered.isEmpty()
                            ? "No resource maps found; custom matches are available."
                            : "Loaded " + discovered.size() + " resources."
            );
            searchPanel.setDiscoveryBusy(false);
            worldPanel.setBusy(false);
        });
        task.setOnFailed(event -> {
            searchPanel.setDiscoveryFailure();
            worldPanel.setPlayerStatus("Player: unavailable");
            showFailure(task.getException());
            searchPanel.setDiscoveryBusy(false);
            worldPanel.setBusy(false);
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
            searchPanel.setStatus("Rendering...");
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
        searchPanel.setStatus("Rendering observed rock geology...");
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
        searchPanel.setStatus("Analyzing prospecting evidence...");
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
        searchPanel.setStatus("Rendering surface resource...");
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
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
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
                EnumSet.of(RenderLayer.TERRAIN, RenderLayer.SURFACE, RenderLayer.MARKERS),
                Optional.of(match),
                new ActualBlockYFilter(min, max),
                Optional.empty(),
                overlays
        );
    }

    private void showResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        mapPanel.show(result.image());
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
        searchPanel.setResult(resultText.toString());
        searchPanel.setStatus("Rendered.");
        setBusy(false);
    }

    private void showSurfaceResult(
            RenderSurfaceResourceMapResult result,
            RenderSurfaceResourceMapRequest request
    ) {
        mapPanel.show(result.image());
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
        searchPanel.setResult(resultText.toString());
        searchPanel.setStatus("Rendered.");
        setBusy(false);
    }

    private void showRockResult(
            RenderRockMapResult result,
            RenderRockMapRequest request
    ) {
        mapPanel.show(result.rendered().image());
        searchPanel.clearRockLegend();
        for (RockLegendEntry entry : result.rendered().legend()) {
            searchPanel.addRockLegend(entry);
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
        searchPanel.setResult(text.toString());
        searchPanel.setStatus("Rock map rendered.");
        setBusy(false);
    }

    private void showProspectingResult(ProspectingAreaResult result) {
        searchPanel.clearProspectingResults();
        for (ProspectingAssessment assessment : result.assessments()) {
            searchPanel.addProspectingResult(
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
            );
        }
        searchPanel.setResult(
                "Prospecting evidence\n"
                        + "Observed saved geology and relative worldgen signals\n"
                        + "Candidates: " + result.assessments().size()
        );
        searchPanel.setStatus("Prospecting analysis complete.");
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
        searchPanel.setStatus("Error: " + conciseMessage(failure));
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
        worldPanel.setBusy(busy);
        searchPanel.setBusy(busy);
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
