package cartographer.ui.workstation;

import cartographer.application.ActualOreOverlaySpec;
import cartographer.application.SurfaceMaterialMatch;
import cartographer.application.SurfaceMaterialPreset;
import cartographer.model.BlockInfo;
import cartographer.resource.ObservedSurfaceResource;
import cartographer.resource.ObservedSurfaceResourceCatalog;
import cartographer.ui.OreResource;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
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
    private final VBox modeContent = new VBox(4);
    private final MapToolPane mapPane = new MapToolPane();
    private final CoverageToolPane coveragePane = new CoverageToolPane();
    private final OreToolPane orePane = new OreToolPane();
    private final SurfaceToolPane surfacePane;
    private final GeologyToolPane geologyPane = new GeologyToolPane();
    private final ProspectingToolPane prospectingPane = new ProspectingToolPane();
    private final RadiusPane radiusPane = new RadiusPane();
    private final Button renderButton = new Button("Render");
    private WorkstationTool mode = WorkstationTool.ORE;
    private boolean globallyBusy;

    public SearchPanel(Runnable onRender) {
        surfacePane = new SurfaceToolPane(this::updateRenderAvailability);
        getStyleClass().add("tool-options");
        renderButton.setOnAction(e -> onRender.run());
        getChildren().addAll(
                new Label("TOOL OPTIONS"),
                modeContent,
                radiusPane,
                new Separator(),
                new HBox(8, renderButton)
        );
        setPrefWidth(270);
        setMode(WorkstationTool.ORE);
    }

    public WorkstationTool selectedMode() {
        return mode;
    }

    public void setMode(WorkstationTool selected) {
        mode = java.util.Objects.requireNonNull(selected, "tool is required");
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
        updateRenderAvailability();
    }

    public String oreResourceText() {
        return orePane.oreResourceText();
    }

    public String prospectingResourceText() {
        return prospectingPane.resourceText();
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

    public Optional<SurfaceMaterialPreset> selectedSurfaceMaterial() {
        return surfacePane.selectedMaterial();
    }

    public Optional<SurfaceMaterialMatch> surfaceMaterialMatch() {
        return surfacePane.materialMatch();
    }

    public String resourceMatch() {
        return orePane.resourceMatch();
    }

    public void setResources(
            List<OreResource> resources,
            Map<Integer, BlockInfo> registry
    ) {
        java.util.Objects.requireNonNull(registry, "registry is required");
        orePane.setResources(resources);
    }

    public void setDiscoveryFailure() {
        orePane.setDiscoveryFailure();
    }

    public Optional<ObservedSurfaceResource> selectedObservedSurfaceResource() {
        return surfacePane.selectedObservedSurfaceResource();
    }

    public List<ObservedSurfaceResource> selectedObservedSurfaceResources() {
        return surfacePane.selectedObservedSurfaceResources();
    }

    public void setObservedSurfaceResources(ObservedSurfaceResourceCatalog catalog) {
        surfacePane.setObservedSurfaceResources(catalog);
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            Set<String> previousKeys
    ) {
        surfacePane.setObservedSurfaceResources(catalog, previousKeys);
    }

    public void setObservedSurfaceResources(
            ObservedSurfaceResourceCatalog catalog,
            String previousKey
    ) {
        surfacePane.setObservedSurfaceResources(catalog, previousKey);
    }

    public void clearObservedSurfaceResources() {
        surfacePane.clearObservedSurfaceResources();
    }

    public void setSurfaceObjectDiscoveryState(SurfaceObjectDiscoveryState state) {
        surfacePane.setDiscoveryState(state);
    }

    public void setBusy(boolean busy) {
        globallyBusy = busy;
        renderButton.setDisable(busy);
        orePane.setBusy(busy);
        surfacePane.setBusy(busy);
        geologyPane.setBusy(busy);
        prospectingPane.setBusy(busy);
        radiusPane.setBusy(busy);
        updateRenderAvailability();
    }

    public void setDiscoveryBusy(boolean busy) {
        globallyBusy = busy;
        renderButton.setDisable(busy);
        orePane.setDiscoveryBusy(busy);
        surfacePane.setDiscoveryBusy(busy);
        geologyPane.setDiscoveryBusy(busy);
        prospectingPane.setDiscoveryBusy(busy);
        radiusPane.setDiscoveryBusy(busy);
        updateRenderAvailability();
    }

    public List<ActualOreOverlaySpec> selectedOverlays() {
        return orePane.selectedOverlays();
    }

    static List<SurfaceMaterialPreset> surfaceMaterials() {
        return SurfaceToolPane.surfaceMaterials();
    }

    private void updateRenderAvailability() {
        if (globallyBusy) {
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
