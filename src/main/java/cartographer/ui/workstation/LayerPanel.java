package cartographer.ui.workstation;

import cartographer.render.RenderLayer;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

public final class LayerPanel extends VBox {
    private final CheckBox terrain = new CheckBox("Terrain");
    private final CheckBox surface = new CheckBox("Surface");
    private final CheckBox soilFertility = new CheckBox("Soil Fertility");
    private final CheckBox markers = new CheckBox("Markers");
    private boolean modeSupported = true;
    private Consumer<Set<RenderLayer>> layersListener = ignored -> { };

    public LayerPanel() {
        super(4);
        getStyleClass().add("layer-panel");
        terrain.getStyleClass().add("layer-row");
        surface.getStyleClass().add("layer-row");
        soilFertility.getStyleClass().add("layer-row");
        markers.getStyleClass().add("layer-row");
        getChildren().addAll(new Label("LAYERS"), terrain, surface, soilFertility, markers);
        terrain.setSelected(true);
        surface.setSelected(true);
        markers.setSelected(true);
        for (CheckBox layer : java.util.List.of(
                terrain,
                surface,
                soilFertility,
                markers
        )) {
            layer.selectedProperty().addListener(
                    (observable, oldValue, selected) ->
                            layersListener.accept(selectedRenderLayers())
            );
        }
    }

    public void setOnLayersChanged(Consumer<Set<RenderLayer>> listener) {
        layersListener = listener == null ? ignored -> { } : listener;
    }

    public Set<RenderLayer> selectedRenderLayers() {
        EnumSet<RenderLayer> layers = EnumSet.noneOf(RenderLayer.class);
        if (terrain.isSelected()) layers.add(RenderLayer.TERRAIN);
        if (surface.isSelected()) layers.add(RenderLayer.SURFACE);
        if (soilFertility.isSelected()) layers.add(RenderLayer.SOIL_FERTILITY);
        if (markers.isSelected()) layers.add(RenderLayer.MARKERS);
        return layers.isEmpty() ? Set.of() : EnumSet.copyOf(layers);
    }

    public void setMode(WorkstationTool mode) {
        boolean supported = mode == WorkstationTool.MAP
                || mode == WorkstationTool.ORE
                || mode == WorkstationTool.SURFACE;
        modeSupported = supported;
        applyDisabledState();
    }

    public void setBusy(boolean busy) {
        terrain.setDisable(busy || !modeSupported);
        surface.setDisable(busy || !modeSupported);
        soilFertility.setDisable(busy || !modeSupported);
        markers.setDisable(busy || !modeSupported);
    }

    private void applyDisabledState() {
        terrain.setDisable(!modeSupported);
        surface.setDisable(!modeSupported);
        soilFertility.setDisable(!modeSupported);
        markers.setDisable(!modeSupported);
    }
}
