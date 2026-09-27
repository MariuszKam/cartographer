package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.render.MapTileRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderTileLayout;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.WorldDataSnapshot;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Creates generation-scoped progressive MAP sessions for selected saves. */
public final class ProgressiveMapSessionFactory {
    private static final int MAX_QUEUED_TILES = 512;
    private static final int DEFAULT_MAPCHUNKS_PER_TILE = 4;

    private final VcdbsReader reader;
    private final SaveSessionFactory saveSessionFactory;
    private final RenderDataCacheStore cacheStore;
    private final RenderTileLayout layout;
    private final MapTileRenderer renderer;
    private final AtomicLong nextGeneration = new AtomicLong();

    public ProgressiveMapSessionFactory(
            VcdbsReader reader,
            SaveSessionFactory saveSessionFactory,
            RenderDataCacheStore cacheStore,
            MapTileRenderer renderer
    ) {
        this(
                reader,
                saveSessionFactory,
                cacheStore,
                new RenderTileLayout(DEFAULT_MAPCHUNKS_PER_TILE),
                renderer
        );
    }

    ProgressiveMapSessionFactory(
            VcdbsReader reader,
            SaveSessionFactory saveSessionFactory,
            RenderDataCacheStore cacheStore,
            RenderTileLayout layout,
            MapTileRenderer renderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.saveSessionFactory = Objects.requireNonNull(
                saveSessionFactory,
                "saveSessionFactory is required"
        );
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.renderer = Objects.requireNonNull(
                renderer,
                "renderer is required"
        );
    }

    public RenderTileLayout layout() {
        return layout;
    }

    public ProgressiveMapSession create(
            Path savePath,
            WorldOverview overview,
            Set<RenderLayer> layers,
            Consumer<ProgressiveMapEvent> listener
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(overview, "overview is required");
        Objects.requireNonNull(layers, "layers are required");
        Objects.requireNonNull(listener, "listener is required");

        WorldDataSnapshot snapshot = WorldDataSnapshot.openOrCreate(
                cacheStore,
                savePath
        ).orElseThrow(
                () -> new IllegalStateException(
                        "Cannot open progressive render-data snapshot"
                )
        );
        ProgressiveTilePipeline pipeline =
                new SaveBackedProgressiveTilePipeline(
                        savePath,
                        overview,
                        saveSessionFactory,
                        reader,
                        snapshot,
                        layout,
                        new MapTileDataLoader(
                                reader,
                                new WorldIndexBatchPlanner(16)
                        ),
                        renderer,
                        layers
                );

        int renderWorkers = Math.clamp(
                Runtime.getRuntime().availableProcessors(),
                1,
                4
        );
        return new ProgressiveMapSession(
                nextGeneration.incrementAndGet(),
                layout,
                pipeline,
                listener,
                MAX_QUEUED_TILES,
                renderWorkers,
                renderWorkers * 2
        );
    }
}
