package cartographer.ui.workstation;

import cartographer.application.*;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.geology.rock.RockColumnSample;
import cartographer.geology.rock.RockMapMode;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.render.RockLegendEntry;
import cartographer.render.RenderLayer;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectSelectionAnalysis;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.save.ReadDiagnostics;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ResultInspectorPane extends VBox {
    private final Label cursorInspection = new Label();
    private final VBox content = new VBox(8);
    private final DiagnosticsPane diagnostics = new DiagnosticsPane();
    private final LayerPanel layerPanel;
    private final TabPane tabs = new TabPane();
    private final Tab inspectTab = new Tab("Inspect");
    private final Tab resultsTab = new Tab("Results");
    private final Tab layersTab = new Tab("Layers");
    private final Tab diagnosticsTab = new Tab("Diagnostics");

    public ResultInspectorPane() {
        this(new LayerPanel());
    }

    public ResultInspectorPane(LayerPanel layerPanel) {
        super(8);
        this.layerPanel = java.util.Objects.requireNonNull(
                layerPanel,
                "layerPanel is required"
        );
        getStyleClass().add("result-inspector");
        setPrefWidth(340);
        setMinWidth(260);

        cursorInspection.setWrapText(true);
        cursorInspection.getStyleClass().add("cursor-inspection");

        inspectTab.setClosable(false);
        resultsTab.setClosable(false);
        layersTab.setClosable(false);
        diagnosticsTab.setClosable(false);

        inspectTab.setContent(scroll(new VBox(8, cursorInspection)));
        resultsTab.setContent(scroll(content));
        layersTab.setContent(scroll(layerPanel));
        diagnosticsTab.setContent(scroll(diagnostics));

        tabs.getTabs().setAll(
                inspectTab,
                resultsTab,
                layersTab,
                diagnosticsTab
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("inspector-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Label header = label("INSPECTOR");
        header.getStyleClass().add("inspector-title");
        getChildren().addAll(header, tabs);
        clear();
    }

    public void clear() {
        clearCursorInspection();
        content.getChildren().setAll(label("No result yet."));
        diagnostics.show(List.of());
        tabs.getSelectionModel().select(resultsTab);
    }

    public void setLayersAvailable(boolean available) {
        layersTab.setDisable(!available);
        if (!available && tabs.getSelectionModel().getSelectedItem() == layersTab) {
            tabs.getSelectionModel().select(resultsTab);
        }
    }

    public void showError(Throwable failure) {
        clearCursorInspection();
        content.getChildren().setAll(label("ERROR"), label(message(failure)));
        diagnostics.show(List.of());
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showOreResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Ore Map"));
        nodes.add(label("Resources: " + result.actualOreOverlays().size()));
        for (ActualOreOverlayResult overlay : result.actualOreOverlays()) {
            var map = overlay.map();
            nodes.add(card(overlay.spec().displayName(),
                    "Blocks", Long.toString(map.matchingBlocks()),
                    "Columns", Integer.toString(map.hitColumns()),
                    "Y range", map.cells().isEmpty() ? "none" : map.minMatchedY() + "–" + map.maxMatchedY()));
        }
        nodes.add(label("Radius: " + request.radius()));
        content.getChildren().setAll(nodes);
        diagnostics.show(oreDiagnostics(result));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showMapResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Map"));
        nodes.add(label("Radius: " + request.radius()));
        nodes.add(label("Layers: " + result.renderReport().layers()));
        nodes.add(label("User markers: " + result.userMarkersDrawn()));
        if (requiresSurfaceData(request)) {
            nodes.add(label("Surface columns: " + result.surface().columnsScanned()));
            nodes.add(label("Water columns: " + result.surface().waterColumns()));
            nodes.add(label("Unknown surface blocks: " + result.surface().unknownSurfaceBlocks()));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(mapDiagnostics(result, request));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showCoverageResult(RenderCoverageMapResult result) {
        RegionCoverageSummary summary = result.summary();
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Coverage"));
        if (summary.empty()) {
            nodes.add(label("No saved mapregions found."));
        } else {
            nodes.add(label("Available mapregions: " + summary.presentCells()));
            nodes.add(label("Grid: " + summary.gridWidth() + "x" + summary.gridHeight()));
            nodes.add(label("Bounding cells: " + summary.possibleCells()));
            nodes.add(label("Missing inside bounds: " + summary.missingCells()));
            nodes.add(label(String.format(java.util.Locale.ROOT,
                    "Coverage inside bounds: %.2f%%", summary.coveragePercentage())));
            nodes.add(label("Region bounds:\nX " + summary.minRegionX() + ".." + summary.maxRegionX()
                    + "\nZ " + summary.minRegionZ() + ".." + summary.maxRegionZ()));
            nodes.add(label(String.format(java.util.Locale.ROOT,
                    "DISPLAY bounds:\nX %.0f..%.0f\nZ %.0f..%.0f",
                    summary.displayMinX(), summary.displayMaxXExclusive() - 1.0,
                    summary.displayMinZ(), summary.displayMaxZExclusive() - 1.0)));
            nodes.add(label("Missing cells mean mapregions absent inside the observed bounding rectangle."
                    + "\nAreas outside those bounds are not classified as missing."));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(diagnostics(result.mapRegionDiagnostics()));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showSurfaceResult(RenderSurfaceResourceMapResult result, RenderSurfaceResourceMapRequest request) {
        if (result.analysis() instanceof SurfaceObjectSelectionAnalysis objectSelection) {
            List<javafx.scene.Node> nodes = new ArrayList<>();
            nodes.add(sectionTitle("Surface Objects"));
            nodes.add(label("Resources: " + objectSelection.resourceCount()));
            nodes.add(label("Total occurrences: " + objectSelection.occurrenceCount()));
            nodes.add(label("Radius: " + request.radius()));
            nodes.add(label("Source: discovery result"));
            for (SurfaceObjectAnalysis objectAnalysis : objectSelection.resources()) {
                nodes.add(card(objectAnalysis.displayName(), "Occurrences",
                        Integer.toString(objectAnalysis.occurrenceCount()),
                        SurfaceObjectPresentation.familyMetricLabel(objectAnalysis.families()),
                        SurfaceObjectPresentation.analysisFamilyText(objectAnalysis),
                        "Registry variants", Integer.toString(objectAnalysis.registryVariantCount())));
            }
            content.getChildren().setAll(nodes);
        } else if (result.analysis() instanceof SurfaceMaterialAnalysis materialAnalysis) {
            content.getChildren().setAll(sectionTitle("Surface Material"),
                    card(materialAnalysis.materialName(), "Matched blocks",
                            Integer.toString(materialAnalysis.matchedBlockCount()),
                            "Areas/deposits", Integer.toString(materialAnalysis.depositCount()),
                            "Radius", Integer.toString(request.radius())),
                    label("Surface material scan complete."));
        } else {
            throw new IllegalStateException("Unsupported surface analysis type");
        }
        diagnostics.show(surfaceDiagnostics(result));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showRockResult(RenderRockMapResult result, RenderRockMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Geology"));
        nodes.add(card(request.mode() == RockMapMode.AT_Y ? "At Y" : "Upper rock",
                "Observed", Long.toString(result.rendered().observedCount()),
                "Rock types", Integer.toString(result.catalog().rocks().size()),
                "Radius", Integer.toString(request.radius())));
        nodes.add(label(request.mode() == RockMapMode.AT_Y
                ? "Y: " + request.y().orElseThrow()
                : "Y range: " + result.minY() + ".." + result.maxYExclusive() + " (exclusive)"));
        nodes.add(label("No rock: " + result.rendered().noRockCount()
                + "\nUnavailable: " + result.rendered().unavailableCount()));
        for (RockLegendEntry entry : result.rendered().legend()) {
            nodes.add(legendLine(entry));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(List.of("Chunks: " + result.chunkStats().fullyDecodedChunks(),
                "Palette rejected: " + result.chunkStats().paletteRejectedChunks(),
                "Failed chunks: " + result.chunkStats().failedChunks(),
                "Y range: " + result.minY() + ".." + result.maxYExclusive()));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showProspectingResult(ProspectingAreaResult result, ProspectingAreaRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Prospecting"));
        nodes.add(label(
                "Resources: "
                        + (request.allResources()
                        ? "All resources"
                        : String.join(", ", request.resources()))
        ));
        nodes.add(label("Radius: " + result.radius()));
        for (ProspectingAssessment assessment : result.assessments()) {
            nodes.add(card(assessment.candidate().resourceKey(), "Rank", assessment.rank().toString(),
                    "Geology", assessment.candidate().evidence().geologyState().toString(),
                    "Compatibility", assessment.compatibility().toString()));
            var evidence = assessment.candidate().evidence();
            String signal = evidence.worldgenSignal().isPresent()
                    ? String.format(java.util.Locale.ROOT, "%.3f relative", evidence.worldgenSignal().getAsDouble())
                    : "unavailable";
            nodes.add(label("Worldgen signal: " + signal
                    + "\nActual ore: " + evidence.actualOreObservation()));
            nodes.add(label("Reasons:\n- " + String.join("\n- ", assessment.reasons())));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(List.of("Assessments: " + result.assessments().size()));
        tabs.getSelectionModel().select(resultsTab);
    }

    public void showRockCursor(Optional<RockColumnSample> sample) {
        sample = java.util.Objects.requireNonNull(sample, "sample is required");
        if (sample.isEmpty()) {
            clearCursorInspection();
            return;
        }
        RockColumnSample value = sample.orElseThrow();
        String details = switch (value.state()) {
            case OBSERVED -> "Rock @ X " + value.worldX()
                    + ", Z " + value.worldZ()
                    + "\n" + value.rock().orElseThrow().code()
                    + " @ Y " + value.rockY().orElseThrow();
            case NO_ROCK -> "Rock @ X " + value.worldX()
                    + ", Z " + value.worldZ()
                    + "\nNo rock observed";
            case UNAVAILABLE -> "Rock @ X " + value.worldX()
                    + ", Z " + value.worldZ()
                    + "\nUnavailable";
        };
        cursorInspection.setText(details);
    }

    public void clearCursorInspection() {
        cursorInspection.setText("Move the cursor over a retained geology or prospecting map to inspect the compact ROCK state.");
    }

    private ScrollPane scroll(Node node) {
        VBox wrapper = new VBox(node);
        wrapper.setPadding(new Insets(6));
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private VBox card(String title, String key1, String value1, String key2, String value2, String key3, String value3) {
        return card(title, List.of(
                key1 + ": " + value1,
                key2 + ": " + value2,
                key3 + ": " + value3));
    }

    private VBox card(String title, String key1, String value1, String key2, String value2,
                      String key3, String value3, String key4, String value4) {
        return card(title, List.of(
                key1 + ": " + value1,
                key2 + ": " + value2,
                key3 + ": " + value3,
                key4 + ": " + value4));
    }

    private VBox card(String title, List<String> rows) {
        Label cardTitle = label(title);
        cardTitle.getStyleClass().add("result-card-title");
        List<javafx.scene.Node> children = new ArrayList<>();
        children.add(cardTitle);
        rows.stream().map(this::label).forEach(children::add);
        VBox card = new VBox(2, children.toArray(javafx.scene.Node[]::new));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("result-card");
        return card;
    }

    private Label sectionTitle(String text) {
        Label title = label(text);
        title.getStyleClass().add("result-section-title");
        return title;
    }

    private HBox legendLine(RockLegendEntry entry) {
        int argb = entry.argb();
        Region swatch = new Region();
        swatch.setMinSize(12, 12);
        swatch.setPrefSize(12, 12);
        swatch.setMaxSize(12, 12);
        swatch.setStyle(String.format(java.util.Locale.ROOT,
                "-fx-background-color: rgba(%d, %d, %d, %.3f);",
                (argb >>> 16) & 0xff, (argb >>> 8) & 0xff, argb & 0xff,
                ((argb >>> 24) & 0xff) / 255.0));
        return new HBox(6, swatch, label(entry.rock().code() + " — " + entry.observedCellCount()));
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private List<String> oreDiagnostics(RenderActualOreMapResult result) {
        List<String> lines = new ArrayList<>(diagnostics(
                result.mapChunkDiagnostics(),
                result.chunkDiagnostics(),
                result.mapRegionDiagnostics(),
                result.actualOreDiagnostics()
        ));
        lines.addAll(renderDataCacheDiagnostics(result.renderDataCacheReport()));
        return lines;
    }

    private List<String> mapDiagnostics(
            RenderActualOreMapResult result,
            RenderActualOreMapRequest request
    ) {
        List<String> lines = new ArrayList<>(requiresSurfaceData(request)
                ? diagnostics(result.mapChunkDiagnostics(), result.chunkDiagnostics())
                : diagnostics(result.mapChunkDiagnostics()));
        lines.addAll(renderDataCacheDiagnostics(result.renderDataCacheReport()));
        return lines;
    }

    private List<String> renderDataCacheDiagnostics(RenderDataCacheReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Render-data cache: " + (report.enabled() ? "enabled" : "disabled"));
        if (report.enabled()) {
            lines.add(cacheArtifactDiagnostics("Terrain", report.terrain()));
            lines.add(cacheArtifactDiagnostics("Surface", report.surface()));
        }
        lines.addAll(report.notes());
        return lines;
    }

    private String cacheArtifactDiagnostics(
            String label,
            RenderDataCacheReport.ArtifactStats stats
    ) {
        return label + " cache: requested " + stats.requested()
                + ", hit " + stats.hits()
                + ", miss " + stats.misses()
                + ", corrupt/incompatible " + stats.corruptOrIncompatible()
                + ", source " + stats.sourceLoaded()
                + ", published " + stats.published()
                + ", skipped incomplete " + stats.skippedIncompleteForPublish()
                + ", world mismatch " + stats.worldMismatches();
    }

    private boolean requiresSurfaceData(RenderActualOreMapRequest request) {
        return request.layers().contains(RenderLayer.SURFACE)
                || request.layers().contains(RenderLayer.SOIL_FERTILITY);
    }

    private List<String> surfaceDiagnostics(RenderSurfaceResourceMapResult result) {
        SurfaceRenderAnalysis analysis = result.analysis();
        List<String> lines = new ArrayList<>(diagnostics(
                result.mapChunkDiagnostics(), result.chunkDiagnostics()));
        if (analysis instanceof SurfaceObjectSelectionAnalysis objectSelection) {
            lines.add("Source: discovery result");
            lines.add("Observed occurrences: " + objectSelection.occurrenceCount());
        }
        lines.addAll(renderDataCacheDiagnostics(result.renderDataCacheReport()));
        return lines;
    }

    private List<String> diagnostics(ReadDiagnostics... diagnostics) {
        List<String> lines = new ArrayList<>();
        for (ReadDiagnostics diagnostic : diagnostics) {
            lines.add("Parsed: " + diagnostic.parsed() + ", skipped: " + diagnostic.skipped() + ", failed: " + diagnostic.failed());
            lines.addAll(diagnostic.notes());
            lines.addAll(diagnostic.failureReasonLines());
        }
        return lines;
    }

    private String message(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getMessage() == null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
