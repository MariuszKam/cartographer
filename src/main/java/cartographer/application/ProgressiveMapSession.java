package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import cartographer.render.RenderLod;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import cartographer.render.RenderTileLayout;
import cartographer.render.RenderedMapTile;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Generation-scoped scheduler/execution owner for progressive map tiles.
 *
 * <p>Source loading and observed-world discovery are serialized on one
 * dedicated source thread. Detached tile data is handed to a bounded CPU
 * render pool. The session itself contains no JavaFX dependency.</p>
 */
public final class ProgressiveMapSession implements AutoCloseable {
    private final long generation;
    private final RenderTileLayout layout;
    private final ProgressiveTilePipeline pipeline;
    private final Consumer<ProgressiveMapEvent> listener;
    private final ProgressiveTileScheduler scheduler;
    private final ExecutorService sourceExecutor;
    private final ThreadPoolExecutor renderExecutor;
    private final Semaphore renderSlots;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean discoveryComplete = new AtomicBoolean();
    private final AtomicBoolean discoveryStopped = new AtomicBoolean();
    private final AtomicReference<RenderLod> currentLod =
            new AtomicReference<>(RenderLod.fullDetail());

    public ProgressiveMapSession(
            long generation,
            RenderTileLayout layout,
            ProgressiveTilePipeline pipeline,
            Consumer<ProgressiveMapEvent> listener,
            int maxQueuedTiles,
            int renderWorkerCount,
            int maxInFlightRenders
    ) {
        if (generation < 0) {
            throw new IllegalArgumentException(
                    "generation must not be negative"
            );
        }
        if (renderWorkerCount <= 0) {
            throw new IllegalArgumentException(
                    "renderWorkerCount must be positive"
            );
        }
        if (maxInFlightRenders < renderWorkerCount) {
            throw new IllegalArgumentException(
                    "maxInFlightRenders must be at least renderWorkerCount"
            );
        }
        this.generation = generation;
        this.layout = Objects.requireNonNull(layout, "layout is required");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline is required");
        this.listener = Objects.requireNonNull(listener, "listener is required");
        this.scheduler = new ProgressiveTileScheduler(maxQueuedTiles);
        this.sourceExecutor = Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "cartographer-progressive-source-" + generation
                    );
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.renderExecutor = new ThreadPoolExecutor(
                renderWorkerCount,
                renderWorkerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxInFlightRenders),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "cartographer-progressive-render-" + generation
                    );
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.renderSlots = new Semaphore(maxInFlightRenders);
    }

    public long generation() {
        return generation;
    }

    public void start(RenderTileCoordinate playerTile) {
        start(playerTile, RenderLod.fullDetail());
    }

    public void start(
            RenderTileCoordinate playerTile,
            RenderLod lod
    ) {
        Objects.requireNonNull(playerTile, "playerTile is required");
        Objects.requireNonNull(lod, "lod is required");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("progressive session already started");
        }
        if (closed.get()) {
            throw new IllegalStateException("progressive session is closed");
        }

        currentLod.set(lod);
        scheduler.offer(
                new RenderTileKey(playerTile, lod),
                ProgressiveTilePriority.BOOTSTRAP
        );
        sourceExecutor.execute(this::sourceLoop);
    }

    public void requestViewport(
            Collection<RenderTileCoordinate> visible,
            Collection<RenderTileCoordinate> prefetch
    ) {
        requestViewport(
                visible,
                prefetch,
                currentLod.get()
        );
    }

    public void requestViewport(
            Collection<RenderTileCoordinate> visible,
            Collection<RenderTileCoordinate> prefetch,
            RenderLod lod
    ) {
        requestViewport(
                visible,
                prefetch,
                visible,
                prefetch,
                lod
        );
    }

    public void requestViewport(
            Collection<RenderTileCoordinate> visible,
            Collection<RenderTileCoordinate> prefetch,
            Collection<RenderTileCoordinate> missingVisible,
            Collection<RenderTileCoordinate> missingPrefetch,
            RenderLod lod
    ) {
        requireStarted();
        Objects.requireNonNull(lod, "lod is required");
        currentLod.set(lod);
        Set<RenderTileKey> interactiveDemand = new LinkedHashSet<>();
        addKeys(interactiveDemand, visible, lod);
        addKeys(interactiveDemand, prefetch, lod);
        scheduler.retainInteractiveDemand(interactiveDemand);
        requestAll(
                missingVisible,
                ProgressiveTilePriority.VIEWPORT,
                lod
        );
        requestAll(
                missingPrefetch,
                ProgressiveTilePriority.PREFETCH,
                lod
        );
    }

    public void requestPlayerRings(
            RenderTileCoordinate playerTile,
            int maxDistance
    ) {
        requireStarted();
        Objects.requireNonNull(playerTile, "playerTile is required");
        if (maxDistance < 0) {
            throw new IllegalArgumentException(
                    "maxDistance must not be negative"
            );
        }
        for (int distance = 1; distance <= maxDistance; distance++) {
            requestAll(
                    playerTile.squareRing(distance),
                    ProgressiveTilePriority.PLAYER_RING,
                    currentLod.get()
            );
        }
    }

    public void acceptDiscoveredMapChunks(
            Collection<MapChunkCoordinate> coordinates
    ) {
        requireStarted();
        Objects.requireNonNull(coordinates, "coordinates are required");
        Set<RenderTileCoordinate> tiles = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            tiles.add(layout.coordinateFor(
                    Objects.requireNonNull(
                            coordinate,
                            "coordinates cannot contain null"
                    )
            ));
        }
        requestAll(
                tiles,
                ProgressiveTilePriority.BACKGROUND,
                currentLod.get()
        );
    }

    public void markDiscoveryComplete() {
        requireStarted();
        discoveryStopped.set(true);
        if (discoveryComplete.compareAndSet(false, true)) {
            publish(new ProgressiveMapEvent.DiscoveryComplete(generation));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.close();
        sourceExecutor.shutdownNow();
        renderExecutor.shutdownNow();
        if (!started.get()) {
            pipeline.close();
        }
        publishEvenWhenClosed(
                new ProgressiveMapEvent.SessionClosed(generation)
        );
    }

    private void requestAll(
            Collection<RenderTileCoordinate> coordinates,
            ProgressiveTilePriority priority,
            RenderLod lod
    ) {
        Objects.requireNonNull(coordinates, "coordinates are required");
        Objects.requireNonNull(lod, "lod is required");
        if (closed.get()) {
            return;
        }
        for (RenderTileCoordinate coordinate : coordinates) {
            scheduler.offer(
                    new RenderTileKey(
                            Objects.requireNonNull(
                                    coordinate,
                                    "coordinates cannot contain null"
                            ),
                            lod
                    ),
                    priority
            );
        }
    }

    private void addKeys(
            Set<RenderTileKey> target,
            Collection<RenderTileCoordinate> coordinates,
            RenderLod lod
    ) {
        for (RenderTileCoordinate coordinate : coordinates) {
            target.add(new RenderTileKey(
                    Objects.requireNonNull(
                            coordinate,
                            "coordinates cannot contain null"
                    ),
                    lod
            ));
        }
    }

    private void sourceLoop() {
        try {
            while (!closed.get()) {
                ProgressiveTileScheduler.ScheduledTile scheduled =
                        scheduler.poll();

                if (scheduled != null) {
                    materialize(scheduled);
                    discoverOneBatch();
                    continue;
                }

                if (!discoveryStopped.get()) {
                    discoverOneBatch();
                    continue;
                }

                scheduled = scheduler.take();
                if (scheduled == null) {
                    return;
                }
                materialize(scheduled);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                pipeline.close();
            } catch (RuntimeException failure) {
                if (!closed.get()) {
                    publish(new ProgressiveMapEvent.DiscoveryFailed(
                            generation,
                            failure.getMessage()
                    ));
                }
            }
        }
    }

    private void materialize(
            ProgressiveTileScheduler.ScheduledTile scheduled
    ) throws InterruptedException {
        renderSlots.acquire();
        if (closed.get()) {
            renderSlots.release();
            return;
        }

        MapTileData data;
        try {
            data = pipeline.load(scheduled.key().coordinate());
        } catch (RuntimeException failure) {
            renderSlots.release();
            scheduler.failed(scheduled.key());
            publishFailure(scheduled.key().coordinate(), failure);
            return;
        }

        try {
            renderExecutor.execute(
                    () -> render(
                            scheduled.key(),
                            data
                    )
            );
        } catch (RuntimeException failure) {
            renderSlots.release();
            scheduler.failed(scheduled.key());
            publishFailure(scheduled.key().coordinate(), failure);
        }
    }

    private void discoverOneBatch() {
        if (closed.get() || discoveryStopped.get()) {
            return;
        }
        try {
            ProgressiveDiscoveryBatch batch =
                    pipeline.discoverNextBatch();
            if (!batch.coordinates().isEmpty()) {
                acceptDiscoveredMapChunks(batch.coordinates());
            }
            if (batch.complete()) {
                markDiscoveryComplete();
            }
        } catch (RuntimeException failure) {
            discoveryStopped.set(true);
            publish(new ProgressiveMapEvent.DiscoveryFailed(
                    generation,
                    failure.getMessage()
            ));
        }
    }

    private void render(
            RenderTileKey key,
            MapTileData data
    ) {
        try {
            RenderedMapTile tile = pipeline.render(key, data);
            scheduler.completed(key);
            if (!closed.get()) {
                publish(new ProgressiveMapEvent.TileReady(
                        generation,
                        tile
                ));
            }
        } catch (RuntimeException failure) {
            scheduler.failed(key);
            publishFailure(key.coordinate(), failure);
        } finally {
            renderSlots.release();
        }
    }

    private void publishFailure(
            RenderTileCoordinate coordinate,
            RuntimeException failure
    ) {
        if (!closed.get()) {
            publish(new ProgressiveMapEvent.TileFailed(
                    generation,
                    coordinate,
                    failure.getMessage()
            ));
        }
    }

    private void publish(ProgressiveMapEvent event) {
        if (!closed.get()) {
            listener.accept(event);
        }
    }

    private void publishEvenWhenClosed(ProgressiveMapEvent event) {
        listener.accept(event);
    }

    private void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException(
                    "progressive session has not been started"
            );
        }
    }
}
