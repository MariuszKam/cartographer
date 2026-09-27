package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import cartographer.progress.ProgressReporter;
import cartographer.render.MapTileRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.RenderLod;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import cartographer.render.RenderTileLayout;
import cartographer.render.RenderedMapTile;
import cartographer.render.TerrainColorRange;
import cartographer.save.ObservedMapChunkCoordinatePage;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldIndexCatalogStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Real source/cache implementation of the progressive tile pipeline.
 *
 * <p>The owning ProgressiveMapSession invokes load/discovery/close on one
 * source thread. Render workers see only detached MapTileData.</p>
 */
final class SaveBackedProgressiveTilePipeline
        implements ProgressiveTilePipeline {
    private static final int CATALOG_BATCH_SIZE = 512;

    private final Path savePath;
    private final WorldOverview overview;
    private final SaveSessionFactory sessionFactory;
    private final VcdbsReader reader;
    private final WorldDataSnapshot snapshot;
    private final RenderTileLayout layout;
    private final MapTileDataLoader dataLoader;
    private final MapTileRenderer renderer;
    private final TerrainColorRange colorRange;
    private final Set<RenderLayer> layers;
    private final ReadDiagnostics sourceDiagnostics = new ReadDiagnostics();
    private final WorldIndexCatalogStore indexStore;
    private final boolean useCompletedCatalog;

    private SaveSession session;
    private OptionalLong sourceCursor = OptionalLong.empty();
    private List<MapChunkCoordinate> catalogCoordinates;
    private int catalogOffset;

    SaveBackedProgressiveTilePipeline(
            Path savePath,
            WorldOverview overview,
            SaveSessionFactory sessionFactory,
            VcdbsReader reader,
            WorldDataSnapshot snapshot,
            RenderTileLayout layout,
            MapTileDataLoader dataLoader,
            MapTileRenderer renderer,
            Set<RenderLayer> layers
    ) {
        this.savePath = Objects.requireNonNull(
                savePath,
                "savePath is required"
        ).toAbsolutePath().normalize();
        this.overview = Objects.requireNonNull(
                overview,
                "overview is required"
        );
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.snapshot = Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.dataLoader = Objects.requireNonNull(
                dataLoader,
                "dataLoader is required"
        );
        this.renderer = Objects.requireNonNull(
                renderer,
                "renderer is required"
        );
        this.layers = Set.copyOf(
                Objects.requireNonNull(layers, "layers are required")
        );
        this.indexStore = snapshot.indexCatalogStore();
        this.useCompletedCatalog = indexStore.mapChunkScanComplete();
        this.colorRange = new TerrainColorRange(
                0,
                Math.max(0, overview.metadata().mapSizeY() - 1)
        );
    }

    @Override
    public MapTileData load(RenderTileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        return dataLoader.load(
                sourceSession(),
                overview.metadata(),
                overview.blockRegistry(),
                snapshot.terrainStore(),
                snapshot.surfaceStore(),
                layout.boundsFor(coordinate),
                requiresSurface()
                        ? MapTileDataRequirement.TERRAIN_AND_SURFACE
                        : MapTileDataRequirement.TERRAIN,
                sourceDiagnostics,
                ProgressReporter.NONE
        );
    }

    @Override
    public RenderedMapTile render(
            RenderTileCoordinate coordinate,
            MapTileData data
    ) {
        return render(
                new RenderTileKey(
                        coordinate,
                        RenderLod.fullDetail()
                ),
                data
        );
    }

    @Override
    public RenderedMapTile render(
            RenderTileKey key,
            MapTileData data
    ) {
        return renderer.render(
                key.coordinate(),
                data.renderData(),
                data.terrainTiles(),
                colorRange,
                RenderStyle.TOPOGRAPHIC,
                layers,
                overview.blockRegistry(),
                key.lod()
        );
    }

    @Override
    public ProgressiveDiscoveryBatch discoverNextBatch() {
        if (useCompletedCatalog) {
            return discoverFromCatalog();
        }

        ObservedMapChunkCoordinatePage page =
                reader.readObservedMapChunkCoordinatePage(
                        sourceSession(),
                        sourceCursor,
                        sourceDiagnostics
                );
        if (!page.coordinates().isEmpty()) {
            indexStore.recordObserved(page.coordinates());
        }
        page.lastPosition().ifPresent(
                position -> sourceCursor = OptionalLong.of(position)
        );
        if (page.complete()) {
            indexStore.markMapChunkScanComplete();
        }
        return new ProgressiveDiscoveryBatch(
                page.coordinates(),
                page.complete()
        );
    }

    @Override
    public void close() {
        SaveSession current = session;
        session = null;
        if (current != null) {
            current.close();
        }
    }

    private ProgressiveDiscoveryBatch discoverFromCatalog() {
        if (catalogCoordinates == null) {
            catalogCoordinates = indexStore.observedMapChunks();
        }
        if (catalogOffset >= catalogCoordinates.size()) {
            return ProgressiveDiscoveryBatch.complete();
        }

        int end = Math.min(
                catalogOffset + CATALOG_BATCH_SIZE,
                catalogCoordinates.size()
        );
        List<MapChunkCoordinate> batch = List.copyOf(
                catalogCoordinates.subList(catalogOffset, end)
        );
        catalogOffset = end;
        return new ProgressiveDiscoveryBatch(
                batch,
                catalogOffset >= catalogCoordinates.size()
        );
    }

    private boolean requiresSurface() {
        return layers.contains(RenderLayer.SURFACE)
                || layers.contains(RenderLayer.SOIL_FERTILITY);
    }

    private SaveSession sourceSession() {
        if (session == null) {
            session = sessionFactory.open(savePath);
        }
        return session;
    }
}
