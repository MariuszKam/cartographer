package cartographer.ui.workstation;

import cartographer.application.*;
import cartographer.geology.rock.RockMapMode;
import cartographer.prospecting.ProspectingAssessment;
import cartographer.render.RockLegendEntry;
import cartographer.save.ReadDiagnostics;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class ResultInspectorPane extends VBox {
    private final VBox content = new VBox(8);
    private final DiagnosticsPane diagnostics = new DiagnosticsPane();

    public ResultInspectorPane() {
        super(8);
        setPrefWidth(310);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        getChildren().addAll(new Label("RESULT INSPECTOR"), scroll, diagnostics);
        clear();
    }

    public void clear() {
        content.getChildren().setAll(new Label("No result yet."));
        diagnostics.show(List.of());
    }

    public void showError(Throwable failure) {
        content.getChildren().setAll(new Label("ERROR"), new Label(message(failure)));
        diagnostics.show(List.of());
    }

    public void showOreResult(RenderActualOreMapResult result, RenderActualOreMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(new Label("ORE MAP"));
        nodes.add(new Label("Resources: " + result.actualOreOverlays().size()));
        for (ActualOreOverlayResult overlay : result.actualOreOverlays()) {
            var map = overlay.map();
            nodes.add(card(overlay.spec().displayName(),
                    "Blocks", Integer.toString(map.matchingBlocks()),
                    "Columns", Integer.toString(map.hitColumns()),
                    "Y range", map.cells().isEmpty() ? "none" : map.minMatchedY() + "–" + map.maxMatchedY()));
        }
        nodes.add(new Label("Radius: " + request.radius()));
        content.getChildren().setAll(nodes);
        diagnostics.show(oreDiagnostics(result));
    }

    public void showSurfaceResult(RenderSurfaceResourceMapResult result, RenderSurfaceResourceMapRequest request) {
        content.getChildren().setAll(new Label("SURFACE RESOURCE"),
                card(request.match().displayName(), "Matches", Integer.toString(result.analysis().matchingBlockCount()),
                        "Deposits", Integer.toString(result.analysis().depositCount()),
                        "Radius", Integer.toString(request.radius())),
                new Label(result.surfaceObjectScanUsed() ? "Exposed obsidian: " + result.exposedObsidianCount()
                        + "\nLoose obsidian: " + result.looseObsidianCount() : "Surface scan complete."));
        diagnostics.show(surfaceDiagnostics(result));
    }

    public void showRockResult(RenderRockMapResult result, RenderRockMapRequest request) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(new Label("GEOLOGY"));
        nodes.add(card(request.mode() == RockMapMode.AT_Y ? "At Y" : "Upper rock",
                "Observed", Integer.toString(result.rendered().observedCount()),
                "Rock types", Integer.toString(result.catalog().rocks().size()),
                "Radius", Integer.toString(request.radius())));
        for (RockLegendEntry entry : result.rendered().legend()) {
            nodes.add(new Label(entry.rock().code() + " — " + entry.observedCellCount()));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(List.of("Chunks: " + result.chunkStats().fullyDecodedChunks(),
                "Palette rejected: " + result.chunkStats().paletteRejectedChunks(),
                "Failed chunks: " + result.chunkStats().failedChunks(),
                "Y range: " + result.minY() + ".." + result.maxYExclusive()));
    }

    public void showProspectingResult(ProspectingAreaResult result) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        nodes.add(new Label("PROSPECTING"));
        nodes.add(new Label("Radius: " + result.radius()));
        for (ProspectingAssessment assessment : result.assessments()) {
            nodes.add(card(assessment.candidate().resourceKey(), "Rank", assessment.rank().toString(),
                    "Geology", assessment.candidate().evidence().geologyState().toString(),
                    "Compatibility", assessment.compatibility().toString()));
            nodes.add(new Label("Actual ore: " + assessment.candidate().evidence().actualOreObservation()));
        }
        content.getChildren().setAll(nodes);
        diagnostics.show(List.of("Assessments: " + result.assessments().size()));
    }

    private VBox card(String title, String key1, String value1, String key2, String value2, String key3, String value3) {
        return new VBox(2, new Label(title), new Label(key1 + ": " + value1), new Label(key2 + ": " + value2), new Label(key3 + ": " + value3));
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
        lines.add("Decoded chunks: " + stats.fullyDecodedChunks());
        lines.add("Palette rejected: " + stats.paletteRejectedChunks());
        lines.add("Failed chunks: " + stats.failedChunks());
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
