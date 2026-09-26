package cartographer.ui.workstation;

import cartographer.render.ActualOreOverlaySpec;
import cartographer.resource.SurfaceMaterialMatch;
import cartographer.application.SurfaceMaterialPreset;
import cartographer.geology.rock.RockIdentity;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.ui.OreResource;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Workstation tool-options facade.
 *
 * <p>Tool-specific JavaFX state lives in dedicated panes. This class keeps the
 * compatibility API used by the Workstation controller while routing requests
 * to the active pane.</p>
 */
public final class SearchPanel extends VBox {
    private final Label contextTitle = new Label("ORES");
    private final Label contextSubtitle = new Label("Source controls");
    private final VBox modeContent = new VBox(6);
    private final MapToolPane mapPane = new MapToolPane();
    private final CoverageToolPane coveragePane = new CoverageToolPane();
    private final OreToolPane orePane = new OreToolPane();
    private final SurfaceToolPane surfacePane;
    private final GeologyToolPane geologyPane = new GeologyToolPane();
    private final ProspectingToolPane prospectingPane = new ProspectingToolPane();
    private final RockHighlightPane rockHighlightPane = new RockHighlightPane();
    private final RadiusPane radiusPane = new RadiusPane();
    private final Button renderButton = new Button("Render");
    private WorkstationTool mode = WorkstationTool.ORE;
    private boolean foregroundBusy;
    private boolean discoveryBusy;

    public SearchPanel(Runnable onRender) {
        surfacePane = new SurfaceToolPane(this::updateRenderAvailability);
        getStyleClass().addAll("tool-options", "context-panel");
        contextTitle.getStyleClass().add("context-title");
        contextSubtitle.getStyleClass().add("context-subtitle");
        renderButton.getStyleClass().add("primary-action");
        renderButton.setMaxWidth(Double.MAX_VALUE);
        renderButton.setOnAction(e -> onRender.run());
        HBox actionRow = new HBox(renderButton);
        HBox.setHgrow(renderButton, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(
                contextTitle,
                contextSubtitle,
                new Separator(),
                modeContent,
                rockHighlightPane,
                radiusPane,
                new Separator(),
                actionRow
        );
        setPrefWidth(286);
        setMaxWidth(Double.MAX_VALUE);
        setMode(WorkstationTool.ORE);
    }

    public WorkstationTool selectedMode() {
        return mode;
    }

    public void setMode(WorkstationTool selected) {
        mode = java.util.Objects.requireNonNull(selected, "tool is required");
        contextTitle.setText(switch (selected) {
            case MAP -> "MAP";
            case COVERAGE -> "COVERAGE";
            case ORE -> "ORES";
            case SURFACE -> "SURFACE";
            case GEOLOGY -> "GEOLOGY";
            case PROSPECTING -> "PROSPECTING";
        });
        contextSubtitle.setText(switch (selected) {
            case MAP -> "Base map source request";
            case COVERAGE -> "Observed mapregion coverage";
            case ORE -> "Authoritative ore scan";
            case SURFACE -> "Surface analysis";
            case GEOLOGY -> "Source-authoritative ROCK";
            case PROSPECTING -> "Fused geology + ore analysis";
        });
        modeContent.getChildren().setAll(switch (selected) {
            case MAP -> mapPane;
            case COVERAGE -> coveragePane;
            case ORE -> orePane;
            case SURFACE -> surfacePane;
            case GEOLOGY -> geologyPane;
            case PROSPECTING -> prospectingPane;
        });
        boolean radiusVisible = selected != WorkstationTool.COVERAGE;
        radiusPane.setManaged(radiusVisible);
        radiusPane.setVisible(radiusVisible);
        renderButton.setText(
                selected == WorkstationTool.PROSPECTING ? "Analyze" : "Render"
        );
        rockHighlightPane.setModeVisible(
                selected == WorkstationTool.GEOLOGY
                        || selected == WorkstationTool.PROSPECTING
        );
        updateRenderAvailability();
    }

    public boolean prospectingAllResources() {
        return prospectingPane.allResources();
    }

    public List<String> prospectingResourceKeys() {
        return prospectingPane.selectedResourceKeys();
    }

    public boolean customYEnabled() {
        return orePane.customYEnabled();
    }

    public boolean multipleResources() {
        return orePane.multipleResources();
    }

    public String yMinText() {
        return orePane.yMinText();
    }

    public String yMaxText() {
        return orePane.yMaxText();
    }

    public boolean rockAtY() {
        return geologyPane.atY();
    }

    public String rockYText() {
        return geologyPane.yText();
    }

    public int selectedRadius() {
        return radiusPane.selectedRadius();
    }

    public void setOnRadiusChanged(Consumer<Integer> listener) {
        radiusPane.setOnRadiusChanged(listener);
    }

    public void setOnSurfaceModeChanged(Consumer<SurfaceToolMode> listener) {
        surfacePane.setOnModeChanged(listener);
    }

    public SurfaceToolMode selectedSurfaceMode() {
        return surfacePane.selectedMode();
    }

    public Optional<SurfaceMaterialMatch> surfaceMaterialMatch() {
        return surfacePane.materialMatch();
    }

    public void setResources(
            List<OreResource> resources
    ) {
        orePane.setResources(resources);
        prospectingPane.setResources(resources);
    }

    public void setDiscoveryFailure() {
        orePane.setDiscoveryFailure();
        prospectingPane.setResources(List.of());
    }

    public List<ObservedSurfaceResource> selectedObservedSurfaceResources() {
        return surfacePane.selectedObservedSurfaceResources();
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            Set<String> previousKeys
    ) {
        surfacePane.setObservedSurfaceResources(catalog, previousKeys);
    }

    public void clearObservedSurfaceResources() {
        surfacePane.clearObservedSurfaceResources();
    }

    public void setSurfaceObjectDiscoveryState(SurfaceObjectDiscoveryState state) {
        surfacePane.setDiscoveryState(state);
    }

    public void setBusy(boolean busy) {
        foregroundBusy = busy;
        renderButton.setDisable(busy);
        orePane.setBusy(busy);
        surfacePane.setBusy(busy);
        geologyPane.setBusy(busy);
        prospectingPane.setBusy(busy);
        radiusPane.setBusy(foregroundBusy || discoveryBusy);
        // Rock highlight works entirely from retained RockMap state and remains local.
        updateRenderAvailability();
    }

    public void setDiscoveryBusy(boolean busy) {
        discoveryBusy = busy;
        surfacePane.setDiscoveryBusy(discoveryBusy);
        radiusPane.setBusy(foregroundBusy || discoveryBusy);
        updateRenderAvailability();
    }

    public void setOnRockHighlightChanged(Consumer<Optional<String>> listener) {
        rockHighlightPane.setOnChanged(listener);
    }

    public void setRockLegend(List<RockIdentity> rocks) {
        rockHighlightPane.setRocks(rocks);
    }

    public Optional<String> selectedRockHighlight() {
        return rockHighlightPane.selectedRockCode();
    }

    public List<ActualOreOverlaySpec> selectedOverlays() {
        return orePane.selectedOverlays();
    }

    static List<SurfaceMaterialPreset> surfaceMaterials() {
        return SurfaceToolPane.surfaceMaterials();
    }

    private void updateRenderAvailability() {
        if (foregroundBusy || discoveryBusy) {
            renderButton.setDisable(true);
            return;
        }
        if (mode != WorkstationTool.SURFACE) {
            renderButton.setDisable(false);
            return;
        }
        renderButton.setDisable(!surfacePane.allowsRender());
    }
}
