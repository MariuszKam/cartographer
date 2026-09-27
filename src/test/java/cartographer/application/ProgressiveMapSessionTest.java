package cartographer.application;

import cartographer.model.MapChunkCoordinate;
import cartographer.render.RenderLod;
import cartographer.render.RenderStyle;
import cartographer.render.RenderTileBounds;
import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;
import cartographer.render.RenderTileLayout;
import cartographer.render.RenderedMapTile;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveMapSessionTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void bootstrapLoadsFirstAndViewportPreemptsBackgroundQueue()
            throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        BlockingPipeline pipeline = new BlockingPipeline(layout);
        List<ProgressiveMapEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        ProgressiveMapSession session = new ProgressiveMapSession(
                7,
                layout,
                pipeline,
                events::add,
                16,
                1,
                1
        );

        RenderTileCoordinate bootstrap = new RenderTileCoordinate(0, 0);
        RenderTileCoordinate background = new RenderTileCoordinate(5, 0);
        RenderTileCoordinate viewport = new RenderTileCoordinate(9, 0);
        session.start(bootstrap);

        assertTrue(pipeline.bootstrapEntered.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.acceptDiscoveredMapChunks(
                List.of(new MapChunkCoordinate(5, 0))
        );
        session.requestViewport(
                List.of(viewport),
                List.of()
        );
        pipeline.releaseBootstrap.countDown();

        assertTrue(pipeline.threeLoads.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();

        assertEquals(
                List.of(bootstrap, viewport, background),
                pipeline.loadOrder.subList(0, 3)
        );
    }

    @Test
    void duplicateRequestsMaterializeTileOnlyOnce() throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        RecordingPipeline pipeline = new RecordingPipeline(layout, 1);
        ProgressiveMapSession session = new ProgressiveMapSession(
                8,
                layout,
                pipeline,
                ignored -> { },
                8,
                1,
                1
        );
        RenderTileCoordinate tile = new RenderTileCoordinate(2, 2);

        session.start(tile);
        session.requestViewport(List.of(tile), List.of(tile));
        session.acceptDiscoveredMapChunks(
                List.of(
                        new MapChunkCoordinate(2, 2),
                        new MapChunkCoordinate(2, 2)
                )
        );

        assertTrue(pipeline.ready.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();

        assertEquals(1, pipeline.loads.get());
        assertEquals(1, pipeline.renders.get());
    }

    @Test
    void localFailureDoesNotStopOtherTiles() throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        FailingPipeline pipeline = new FailingPipeline(
                layout,
                new RenderTileCoordinate(1, 0)
        );
        List<ProgressiveMapEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        ProgressiveMapSession session = new ProgressiveMapSession(
                9,
                layout,
                pipeline,
                events::add,
                8,
                1,
                1
        );

        session.start(new RenderTileCoordinate(1, 0));
        session.requestViewport(
                List.of(new RenderTileCoordinate(2, 0)),
                List.of()
        );

        assertTrue(pipeline.terminal.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();

        assertTrue(events.stream().anyMatch(
                event -> event instanceof ProgressiveMapEvent.TileFailed
        ));
        assertTrue(events.stream().anyMatch(
                event -> event instanceof ProgressiveMapEvent.TileReady
        ));
    }

    @Test
    void closeRejectsLateTileReadyPublication() throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        LateRenderPipeline pipeline = new LateRenderPipeline(layout);
        List<ProgressiveMapEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        ProgressiveMapSession session = new ProgressiveMapSession(
                10,
                layout,
                pipeline,
                events::add,
                8,
                1,
                1
        );

        session.start(new RenderTileCoordinate(0, 0));
        assertTrue(pipeline.renderEntered.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();
        pipeline.releaseRender.countDown();
        assertTrue(pipeline.renderFinished.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));

        assertFalse(events.stream().anyMatch(
                event -> event instanceof ProgressiveMapEvent.TileReady
        ));
        assertTrue(events.stream().allMatch(
                event -> event.generation() == 10
        ));
    }

    private static MapTileData data(
            RenderTileLayout layout,
            RenderTileCoordinate coordinate
    ) {
        RenderTileBounds bounds = layout.boundsFor(coordinate);
        return new MapTileData(
                bounds,
                Optional.of(bounds),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                MapTileDataRequirement.TERRAIN
        );
    }

    private static RenderedMapTile rendered(
            RenderTileLayout layout,
            RenderTileCoordinate coordinate
    ) {
        RenderTileBounds bounds = layout.boundsFor(coordinate);
        return new RenderedMapTile(
                coordinate,
                bounds,
                new BufferedImage(
                        Math.toIntExact(bounds.widthBlocks()),
                        Math.toIntExact(bounds.heightBlocks()),
                        BufferedImage.TYPE_INT_ARGB
                ),
                1,
                0,
                RenderStyle.SIMPLE
        );
    }

    private static class RecordingPipeline
            implements ProgressiveTilePipeline {
        final RenderTileLayout layout;
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger renders = new AtomicInteger();
        final CountDownLatch ready;

        RecordingPipeline(RenderTileLayout layout, int expected) {
            this.layout = layout;
            this.ready = new CountDownLatch(expected);
        }

        @Override
        public MapTileData load(RenderTileCoordinate coordinate) {
            loads.incrementAndGet();
            return data(layout, coordinate);
        }

        @Override
        public RenderedMapTile render(
                RenderTileCoordinate coordinate,
                MapTileData data
        ) {
            renders.incrementAndGet();
            ready.countDown();
            return rendered(layout, coordinate);
        }
    }

    private static final class BlockingPipeline extends RecordingPipeline {
        private final List<RenderTileCoordinate> loadOrder =
                Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch bootstrapEntered = new CountDownLatch(1);
        private final CountDownLatch releaseBootstrap = new CountDownLatch(1);
        private final CountDownLatch threeLoads = new CountDownLatch(3);

        private BlockingPipeline(RenderTileLayout layout) {
            super(layout, 3);
        }

        @Override
        public MapTileData load(RenderTileCoordinate coordinate) {
            loadOrder.add(coordinate);
            threeLoads.countDown();
            if (loadOrder.size() == 1) {
                bootstrapEntered.countDown();
                await(releaseBootstrap);
            }
            return super.load(coordinate);
        }
    }

    private static final class FailingPipeline extends RecordingPipeline {
        private final RenderTileCoordinate failing;
        private final CountDownLatch terminal;

        private FailingPipeline(
                RenderTileLayout layout,
                RenderTileCoordinate failing
        ) {
            super(layout, 1);
            this.failing = failing;
            this.terminal = new CountDownLatch(2);
        }

        @Override
        public MapTileData load(RenderTileCoordinate coordinate) {
            if (coordinate.equals(failing)) {
                terminal.countDown();
                throw new IllegalStateException("intentional failure");
            }
            return super.load(coordinate);
        }

        @Override
        public RenderedMapTile render(
                RenderTileCoordinate coordinate,
                MapTileData data
        ) {
            try {
                return super.render(coordinate, data);
            } finally {
                terminal.countDown();
            }
        }
    }

    private static final class LateRenderPipeline extends RecordingPipeline {
        private final CountDownLatch renderEntered = new CountDownLatch(1);
        private final CountDownLatch releaseRender = new CountDownLatch(1);
        private final CountDownLatch renderFinished = new CountDownLatch(1);

        private LateRenderPipeline(RenderTileLayout layout) {
            super(layout, 1);
        }

        @Override
        public RenderedMapTile render(
                RenderTileCoordinate coordinate,
                MapTileData data
        ) {
            renderEntered.countDown();
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        releaseRender.await();
                        break;
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
                return rendered(layout, coordinate);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                renderFinished.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", exception);
        }
    }
    @Test
    void discoveryBatchQueuesBackgroundTileAndPipelineClosesOnSourceThread()
            throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        DiscoveryPipeline pipeline = new DiscoveryPipeline(layout);
        List<ProgressiveMapEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        ProgressiveMapSession session = new ProgressiveMapSession(
                11,
                layout,
                pipeline,
                events::add,
                8,
                1,
                1
        );

        session.start(new RenderTileCoordinate(0, 0));

        assertTrue(pipeline.twoLoads.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        assertTrue(pipeline.discoveryCalled.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();
        assertTrue(pipeline.closed.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));

        assertEquals(
                List.of(
                        new RenderTileCoordinate(0, 0),
                        new RenderTileCoordinate(4, 0)
                ),
                pipeline.loadOrder.subList(0, 2)
        );
        assertEquals(
                pipeline.sourceThreadName,
                pipeline.closeThreadName
        );
        assertTrue(events.stream().anyMatch(
                event -> event
                        instanceof ProgressiveMapEvent.DiscoveryComplete
        ));
    }

    private static final class DiscoveryPipeline
            extends RecordingPipeline {
        private final List<RenderTileCoordinate> loadOrder =
                Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch twoLoads = new CountDownLatch(2);
        private final CountDownLatch discoveryCalled =
                new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicInteger discoveryCalls = new AtomicInteger();
        private volatile String sourceThreadName;
        private volatile String closeThreadName;

        private DiscoveryPipeline(RenderTileLayout layout) {
            super(layout, 2);
        }

        @Override
        public MapTileData load(RenderTileCoordinate coordinate) {
            if (sourceThreadName == null) {
                sourceThreadName = Thread.currentThread().getName();
            }
            loadOrder.add(coordinate);
            twoLoads.countDown();
            return super.load(coordinate);
        }

        @Override
        public ProgressiveDiscoveryBatch discoverNextBatch() {
            discoveryCalled.countDown();
            if (discoveryCalls.getAndIncrement() == 0) {
                return new ProgressiveDiscoveryBatch(
                        List.of(new MapChunkCoordinate(4, 0)),
                        true
                );
            }
            return ProgressiveDiscoveryBatch.completed();
        }

        @Override
        public void close() {
            closeThreadName = Thread.currentThread().getName();
            closed.countDown();
        }
    }

    @Test
    void viewportLodFlowsThroughSchedulerIntoRenderPipeline()
            throws Exception {
        RenderTileLayout layout = new RenderTileLayout(1);
        LodPipeline pipeline = new LodPipeline(layout);
        ProgressiveMapSession session = new ProgressiveMapSession(
                12,
                layout,
                pipeline,
                ignored -> { },
                8,
                1,
                1
        );
        RenderTileCoordinate coordinate =
                new RenderTileCoordinate(0, 0);

        session.start(coordinate, RenderLod.LOD_3);

        assertTrue(pipeline.rendered.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ));
        session.close();

        assertEquals(RenderLod.LOD_3, pipeline.renderedLod);
    }

    private static final class LodPipeline extends RecordingPipeline {
        private final CountDownLatch rendered = new CountDownLatch(1);
        private volatile RenderLod renderedLod;

        private LodPipeline(RenderTileLayout layout) {
            super(layout, 1);
        }

        @Override
        public RenderedMapTile render(
                RenderTileKey key,
                MapTileData data
        ) {
            renderedLod = key.lod();
            rendered.countDown();
            return rendered(layout, key.coordinate());
        }
    }

}
