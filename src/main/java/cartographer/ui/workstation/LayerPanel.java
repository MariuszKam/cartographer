package cartographer.ui.workstation;

import cartographer.render.RenderLayer;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.EnumSet;
import java.util.Set;

public final class LayerPanel extends VBox {
    private final CheckBox terrain = new CheckBox("Terrain");
    private final CheckBox surface = new CheckBox("Surface");
    private final CheckBox markers = new CheckBox("Markers");
    private boolean modeSupported = true;

    public LayerPanel() {
        super(4);
        getStyleClass().add("layer-panel");
        terrain.getStyleClass().add("layer-row");
        surface.getStyleClass().add("layer-row");
        markers.getStyleClass().add("layer-row");
        getChildren().addAll(new Label("LAYERS"), terrain, surface, markers);
        terrain.setSelected(true);
        surface.setSelected(true);
        markers.setSelected(true);
    }

    public Set<RenderLayer> selectedRenderLayers() {
        EnumSet<RenderLayer> layers = EnumSet.noneOf(RenderLayer.class);
        if (terrain.isSelected()) layers.add(RenderLayer.TERRAIN);
        if (surface.isSelected()) layers.add(RenderLayer.SURFACE);
        if (markers.isSelected()) layers.add(RenderLayer.MARKERS);
        return layers.isEmpty() ? Set.of() : EnumSet.copyOf(layers);
    }

    public void setMode(SearchPanel.SearchMode mode) {
        boolean supported = mode == SearchPanel.SearchMode.ORE
                || mode == SearchPanel.SearchMode.SURFACE;
        modeSupported = supported;
        applyDisabledState();
    }

    public void setBusy(boolean busy) {
        terrain.setDisable(busy || !modeSupported);
        surface.setDisable(busy || !modeSupported);
        markers.setDisable(busy || !modeSupported);
    }

    private void applyDisabledState() {
        terrain.setDisable(!modeSupported);
        surface.setDisable(!modeSupported);
        markers.setDisable(!modeSupported);
    }
}
