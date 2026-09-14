package cartographer.ui.workstation;

import cartographer.application.*;
import cartographer.geology.rock.RockMapMode;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.render.RockLegendEntry;
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
        nodes.add(label("ORE MAP"));
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
        content.getChildren().setAll(label("SURFACE RESOURCE"),
                card(request.match().displayName(), "Matches", Integer.toString(result.analysis().matchingBlockCount()),
                        "Deposits", Integer.toString(result.analysis().depositCount()),
                        "Radius", Integer.toString(request.radius())),
                label(result.surfaceObjectScanUsed() ? "Exposed obsidian: " + result.exposedObsidianCount()
                        + "\nLoose obsidian: " + result.looseObsidianCount() : "Surface scan complete."));
        diagnostics.show(surfaceDiagnostics(result));
    }

    public void showRockResult(RenderRockMapResult result, RenderRockMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(label("GEOLOGY"));
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
        nodes.add(label("PROSPECTING"));
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
        VBox card = new VBox(2, label(title), label(key1 + ": " + value1), label(key2 + ": " + value2), label(key3 + ": " + value3));
        card.getStyleClass().add("result-card");
        return card;
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
        List<String> lines = new ArrayList<>(diagnostics(result.mapChunkDiagnostics(), result.chunkDiagnostics()));
        var stats = result.surfaceObjectChunkStats();
        lines.add("Registry variants: " + result.surfaceObjectRegistryVariants());
        lines.add("Positions inspected: " + result.surfaceObjectPositionsInspected());
        lines.add("Unavailable positions: " + result.surfaceObjectUnavailablePositions());
        lines.add("Observed surface objects: " + result.surfaceObjectObservedTargets());
        lines.add("Not observed surface objects: " + result.surfaceObjectNotObservedTargets());
        lines.add("Decoded chunks: " + stats.fullyDecodedChunks());
        lines.add("Palette rejected: " + stats.paletteRejectedChunks());
        lines.add("Missing/requested-but-not-found chunks: "
                + (stats.uniquePositionsRequested() - stats.rowsFound()));
        lines.add("Failed chunks: " + stats.failedChunks());
        if (result.surfaceObjectRegistryVariants() == 0) {
            lines.add("No block registry codes matched the surface resource families.");
        }
        int observationLimit = Math.min(20, result.analysis().matchingBlocks().size());
        if (observationLimit > 0) {
            lines.add("Observations:");
            for (int index = 0; index < observationLimit; index++) {
                var point = result.analysis().matchingBlocks().get(index);
                lines.add("  " + point.blockCode() + " @ " + point.worldX() + ", "
                        + point.y() + ", " + point.worldZ());
            }
        }
        int depositLimit = Math.min(5, result.analysis().deposits().size());
        if (depositLimit > 0) {
            lines.add("Largest deposits:");
            for (int index = 0; index < depositLimit; index++) {
                var deposit = result.analysis().deposits().get(index);
                lines.add("  " + (index + 1) + ". blocks=" + deposit.blockCount()
                        + " Y=" + deposit.minY() + ".." + deposit.maxY());
            }
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
