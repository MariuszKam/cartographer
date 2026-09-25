package cartographer.ui.workstation;

import cartographer.application.MapDecorationState;
import cartographer.application.PreparedMapData;
import cartographer.progress.ProgressReporter;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.EnvironmentOverlayRenderer;
import cartographer.render.GeologyOverlayRenderer;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;
import cartographer.render.RenderedMap;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.SystemMarkerOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceObjectSelectionAnalysis;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Set;

/**
 * Rebuilds the currently displayed map from retained compact state only.
 *
 * <p>This compositor never opens the save, cache, HOME store or marker store.
 * All required state must already be retained in the MapFrame.</p>
 */
public final class MapFrameCompositor {
    private final MapRenderer mapRenderer;
    private final ActualOreOverlayPainter oreOverlayPainter;
    private final SurfaceResourceOverlayRenderer surfaceOverlayRenderer;
    private final EnvironmentOverlayRenderer environmentOverlayRenderer;
    private final GeologyOverlayRenderer geologyOverlayRenderer;
    private final SystemMarkerOverlayRenderer systemMarkerOverlayRenderer;
    private final UserMarkerRenderer userMarkerRenderer;

    public MapFrameCompositor() {
        this(
                new MapRenderer(),
                new ActualOreOverlayPainter(),
                new SurfaceResourceOverlayRenderer(),
                new EnvironmentOverlayRenderer(),
                new GeologyOverlayRenderer(),
                new SystemMarkerOverlayRenderer(),
                new UserMarkerRenderer()
        );
    }

    MapFrameCompositor(
            MapRenderer mapRenderer,
            ActualOreOverlayPainter oreOverlayPainter,
            SurfaceResourceOverlayRenderer surfaceOverlayRenderer,
            EnvironmentOverlayRenderer environmentOverlayRenderer,
            GeologyOverlayRenderer geologyOverlayRenderer,
            SystemMarkerOverlayRenderer systemMarkerOverlayRenderer,
            UserMarkerRenderer userMarkerRenderer
    ) {
        this.mapRenderer = Objects.requireNonNull(mapRenderer, "mapRenderer is required");
        this.oreOverlayPainter = Objects.requireNonNull(
                oreOverlayPainter, "oreOverlayPainter is required");
        this.surfaceOverlayRenderer = Objects.requireNonNull(
                surfaceOverlayRenderer, "surfaceOverlayRenderer is required");
        this.environmentOverlayRenderer = Objects.requireNonNull(
                environmentOverlayRenderer, "environmentOverlayRenderer is required");
        this.geologyOverlayRenderer = Objects.requireNonNull(
                geologyOverlayRenderer, "geologyOverlayRenderer is required");
        this.systemMarkerOverlayRenderer = Objects.requireNonNull(
                systemMarkerOverlayRenderer, "systemMarkerOverlayRenderer is required");
        this.userMarkerRenderer = Objects.requireNonNull(
                userMarkerRenderer, "userMarkerRenderer is required");
    }

    public BufferedImage recompose(
            MapFrame frame,
            Set<RenderLayer> layers,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(frame, "frame is required");
        Objects.requireNonNull(layers, "layers are required");
        Objects.requireNonNull(progress, "progress is required");
        if (!frame.supportsLocalRecomposition(layers)) {
            throw new IllegalStateException(
                    "Retained frame does not contain the data required by the selected layers"
            );
        }

        PreparedMapData prepared = frame.preparedMapData().orElseThrow();
        MapDecorationState decorations = frame.decorationState().orElseThrow();
        RenderOptions base = prepared.options();
        RenderOptions options = new RenderOptions(
                base.radiusBlocks(),
                base.pixelsPerBlock(),
                base.style(),
                layers
        );

        cartographer.application.PreparedSurfaceData surface =
                prepared.surface();
        cartographer.scanner.SurfaceMap exactSoil =
                layers.contains(RenderLayer.SOIL_FERTILITY)
                        ? surface.requireAnalysis().map()
                        : null;

        RenderedMap rendered = mapRenderer.render(
                prepared.center(),
                prepared.player(),
                decorations.home(),
                prepared.terrain(),
                surface.renderData(),
                exactSoil,
                prepared.registry(),
                options,
                progress
        );
        if (!rendered.geometry().equals(frame.geometry())) {
            throw new IllegalStateException(
                    "Local recomposition changed map geometry"
            );
        }

        BufferedImage image = rendered.image();
        boolean markers = layers.contains(RenderLayer.MARKERS);
        boolean environment = layers.contains(RenderLayer.ENVIRONMENT);
        boolean geology = layers.contains(RenderLayer.GEOLOGY);

        if (environment) {
            environmentOverlayRenderer.draw(
                    image,
                    prepared.center(),
                    options.radiusBlocks(),
                    frame.mapRegionOverlayState()
                            .orElseThrow()
                            .environmentProfiles()
                            .orElseThrow()
            );
        }
        if (geology) {
            geologyOverlayRenderer.draw(
                    image,
                    prepared.center(),
                    options.radiusBlocks(),
                    frame.mapRegionOverlayState()
                            .orElseThrow()
                            .geologySummaries()
                            .orElseThrow()
            );
        }

        switch (frame.tool()) {
            case MAP -> {
                // Base map is complete after MapRenderer.
            }
            case ORE -> {
                oreOverlayPainter.paint(
                        image,
                        frame.actualOreOverlays(),
                        prepared.center(),
                        options.radiusBlocks()
                );

            }
            case SURFACE -> {
                var analysis = frame.surfaceAnalysis().orElseThrow();
                if (analysis instanceof SurfaceMaterialAnalysis material) {
                    surfaceOverlayRenderer.drawMaterial(
                            image,
                            prepared.center(),
                            options.radiusBlocks(),
                            material,
                            prepared.player(),
                            decorations.home(),
                            markers
                    );
                } else if (analysis instanceof SurfaceObjectSelectionAnalysis objects) {
                    surfaceOverlayRenderer.drawObjects(
                            image,
                            prepared.center(),
                            options.radiusBlocks(),
                            objects,
                            prepared.player(),
                            decorations.home(),
                            markers
                    );
                } else {
                    throw new IllegalStateException(
                            "Unsupported retained Surface analysis: "
                                    + analysis.getClass().getName()
                    );
                }
            }
            case COVERAGE, GEOLOGY, PROSPECTING -> throw new IllegalStateException(
                    "Tool does not support local base-layer recomposition: " + frame.tool()
            );
        }

        boolean repaintSystemMarkers =
                markers
                        && (environment
                        || geology
                        || (frame.tool() == WorkstationTool.ORE
                        && !frame.actualOreOverlays().isEmpty()));
        if (repaintSystemMarkers) {
            systemMarkerOverlayRenderer.draw(
                    image,
                    prepared.center(),
                    prepared.player(),
                    decorations.home(),
                    options.radiusBlocks()
            );
        }

        if (markers && !decorations.userMarkers().isEmpty()) {
            userMarkerRenderer.draw(
                    image,
                    prepared.center(),
                    options.radiusBlocks(),
                    decorations.userMarkers(),
                    prepared.metadata()
            );
        }
        return image;
    }
}
