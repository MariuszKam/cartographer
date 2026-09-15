package cartographer.ui.workstation;

import cartographer.application.*;
import cartographer.geology.rock.RockMapMode;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.render.RockLegendEntry;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceObjectAnalysis;
import cartographer.resource.SurfaceObjectSelectionAnalysis;
import cartographer.resource.SurfaceObjectPresentation;
import cartographer.resource.SurfaceRenderAnalysis;
import cartographer.save.ReadDiagnostics;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class ResultInspectorPane extends VBox {
    private final VBox content = new VBox(8);
    private final DiagnosticsPane diagnostics = new DiagnosticsPane();

    public ResultInspectorPane() {
        super(8);
        getStyleClass().add("result-inspector");
        setPrefWidth(290);
        VBox scrollContent = new VBox(8, content, diagnostics);
        scrollContent.setPadding(new Insets(4));
        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Label header = label("RESULT INSPECTOR");
        header.getStyleClass().add("inspector-title");
        getChildren().addAll(header, scroll);
        clear();
    }

    public void clear() {
        content.getChildren().setAll(label("No result yet."));
        diagnostics.show(List.of());
    }

    public void showError(Throwable failure) {
        content.getChildren().setAll(label("ERROR"), label(message(failure)));
        diagnostics.show(List.of());
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
    }

    public void showProspectingResult(ProspectingAreaResult result, ProspectingAreaRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(sectionTitle("Prospecting"));
        nodes.add(label("Resource: " + request.resource().orElse("All resources")));
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
        return diagnostics(result.mapChunkDiagnostics(), result.chunkDiagnostics(), result.mapRegionDiagnostics(), result.actualOreDiagnostics());
    }

    private List<String> surfaceDiagnostics(RenderSurfaceResourceMapResult result) {
        SurfaceRenderAnalysis analysis = result.analysis();
        List<String> lines = new ArrayList<>(diagnostics(
                result.mapChunkDiagnostics(), result.chunkDiagnostics()));
        if (analysis instanceof SurfaceObjectSelectionAnalysis objectSelection) {
            lines.add("Source: discovery result");
            lines.add("Observed occurrences: " + objectSelection.occurrenceCount());
        }
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
