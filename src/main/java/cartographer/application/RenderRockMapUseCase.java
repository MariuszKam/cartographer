package cartographer.application;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.geology.rock.RockStreamingSession;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.snapshot.SnapshotUpperRockReader;
import cartographer.snapshot.SnapshotUpperRockRenderReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.scanner.ActualBlockYFilter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RenderRockMapUseCase {
    private final VcdbsReader reader;
    private final SaveSessionFactory sessionFactory;
    private final RockMapRenderer renderer;
    private final Optional<SnapshotUpperRockReader> snapshotReader;
    private final Optional<SnapshotUpperRockRenderReader> snapshotRenderReader;
    private final Optional<SnapshotWorldHeaderReader> snapshotHeaderReader;
    private final OreChunkPositionPlanner positionPlanner =
            new OreChunkPositionPlanner();

    public RenderRockMapUseCase(
            VcdbsReader reader,
            RockMapRenderer renderer,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                renderer,
                sessionFactory,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    public RenderRockMapUseCase(
            VcdbsReader reader,
            RockMapRenderer renderer,
            SaveSessionFactory sessionFactory
    ) {
        this(
                reader,
                renderer,
                sessionFactory,
                Optional.empty()
        );
    }

    RenderRockMapUseCase(
            VcdbsReader reader,
            RockMapRenderer renderer,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        this.renderer = Objects.requireNonNull(
                renderer,
                "renderer is required"
        );
        Optional<RenderDataCacheStore> cache =
                Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache option is required"
                );
        this.snapshotReader = cache.map(SnapshotUpperRockReader::new);
        this.snapshotRenderReader =
                cache.map(store -> new SnapshotUpperRockRenderReader(
                        store,
                        this.renderer
                ));
        this.snapshotHeaderReader =
                cache.map(SnapshotWorldHeaderReader::new);
    }

    public RockMapRenderResult renderRetained(RockMap rockMap) {
        return renderRetained(rockMap, Optional.empty());
    }

    public RockMapRenderResult renderRetained(
            RockMap rockMap,
            Optional<String> highlightRockCode
    ) {
        Objects.requireNonNull(rockMap, "rock map is required");
        Objects.requireNonNull(highlightRockCode, "highlightRockCode is required");
        return renderer.render(rockMap, highlightRockCode);
    }

    public RenderRockMapResult execute(RenderRockMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    /**
     * Executes with exact retained ROCK state for interactive callers.
     */
    public RenderRockMapResult execute(
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        return executeInternal(request, progress, true);
    }

    /**
     * Executes without retaining request-shaped ROCK state when a compatible
     * UPPER_ROCK snapshot can render directly into the bounded raster.
     */
    public RenderRockMapResult executeRenderOnly(
            RenderRockMapRequest request
    ) {
        return executeRenderOnly(request, ProgressReporter.NONE);
    }

    public RenderRockMapResult executeRenderOnly(
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        return executeInternal(request, progress, false);
    }

    private RenderRockMapResult executeInternal(
            RenderRockMapRequest request,
            ProgressReporter progress,
            boolean retainRockMap
    ) {
        Objects.requireNonNull(request, "rock map request is required");
        Objects.requireNonNull(progress, "progress is required");

        Optional<RenderRockMapResult> snapshot = retainRockMap
                ? executeSnapshot(request, progress)
                : executeSnapshotRenderOnly(request, progress);
        if (snapshot.isPresent()) {
            return snapshot.orElseThrow();
        }

        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return executeSource(
                    session,
                    request,
                    progress,
                    retainRockMap
            );
        }
    }

    private Optional<RenderRockMapResult> executeSnapshot(
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        if (request.mode() != RockMapMode.UPPER_ROCK
                || snapshotReader.isEmpty()
                || snapshotHeaderReader.isEmpty()) {
            return Optional.empty();
        }

        var header = snapshotHeaderReader.orElseThrow()
                .read(request.savePath());
        if (header.isEmpty()) {
            return Optional.empty();
        }
        WorldMetadata metadata = header.orElseThrow().metadata();
        WorldPosition center;
        if (request.center().isPresent()) {
            center = request.center().orElseThrow();
        } else if (header.orElseThrow().player().isPresent()) {
            center = header.orElseThrow().player().orElseThrow();
        } else {
            return Optional.empty();
        }

        int minY = request.minY().orElse(0);
        int maxYExclusive = request.maxYExclusive()
                .orElse(metadata.mapSizeY());
        if (minY != 0 || maxYExclusive != metadata.mapSizeY()) {
            return Optional.empty();
        }

        RockCatalog catalog = RockCatalog.from(
                header.orElseThrow().blockRegistry()
        );
        if (catalog.rocks().isEmpty()) {
            return Optional.empty();
        }

        progress.start("Reading geology from world snapshot");
        Optional<RockMap> snapshotMap = snapshotReader.orElseThrow().read(
                request.savePath(),
                metadata,
                header.orElseThrow().blockRegistry(),
                center,
                request.radius()
        );
        if (snapshotMap.isEmpty()) {
            return Optional.empty();
        }

        RockMap map = snapshotMap.orElseThrow();
        progress.start("Rendering geology map");
        RockMapRenderResult rendered = renderer.render(map);
        progress.done("Rendered geology from world snapshot");
        return Optional.of(new RenderRockMapResult(
                Optional.of(map),
                rendered,
                catalog,
                new SelectiveChunkStreamStats(
                        0, 0, 0, 0, 0, 0, 0, 0
                ),
                new ReadDiagnostics(),
                center,
                minY,
                maxYExclusive
        ));
    }

    private Optional<RenderRockMapResult> executeSnapshotRenderOnly(
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        if (request.mode() != RockMapMode.UPPER_ROCK
                || snapshotRenderReader.isEmpty()
                || snapshotHeaderReader.isEmpty()) {
            return Optional.empty();
        }

        var header = snapshotHeaderReader.orElseThrow()
                .read(request.savePath());
        if (header.isEmpty()) {
            return Optional.empty();
        }
        WorldMetadata metadata = header.orElseThrow().metadata();
        WorldPosition center;
        if (request.center().isPresent()) {
            center = request.center().orElseThrow();
        } else if (header.orElseThrow().player().isPresent()) {
            center = header.orElseThrow().player().orElseThrow();
        } else {
            return Optional.empty();
        }

        int minY = request.minY().orElse(0);
        int maxYExclusive = request.maxYExclusive()
                .orElse(metadata.mapSizeY());
        if (minY != 0 || maxYExclusive != metadata.mapSizeY()) {
            return Optional.empty();
        }

        RockCatalog catalog = RockCatalog.from(
                header.orElseThrow().blockRegistry()
        );
        if (catalog.rocks().isEmpty()) {
            return Optional.empty();
        }

        progress.start("Rendering geology from world snapshot");
        Optional<RockMapRenderResult> rendered =
                snapshotRenderReader.orElseThrow().read(
                        request.savePath(),
                        metadata,
                        header.orElseThrow().blockRegistry(),
                        center,
                        request.radius()
                );
        if (rendered.isEmpty()) {
            return Optional.empty();
        }

        progress.done("Rendered geology from world snapshot");
        return Optional.of(new RenderRockMapResult(
                Optional.empty(),
                rendered.orElseThrow(),
                catalog,
                new SelectiveChunkStreamStats(
                        0, 0, 0, 0, 0, 0, 0, 0
                ),
                new ReadDiagnostics(),
                center,
                minY,
                maxYExclusive
        ));
    }

    public RenderRockMapResult execute(
            SaveSession saveSession,
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        return executeSource(
                saveSession,
                request,
                progress,
                true
        );
    }

    private RenderRockMapResult executeSource(
            SaveSession saveSession,
            RenderRockMapRequest request,
            ProgressReporter progress,
            boolean retainRockMap
    ) {
        Objects.requireNonNull(saveSession, "session is required");
        Objects.requireNonNull(request, "rock map request is required");
        Objects.requireNonNull(progress, "progress is required");
        saveSession.requireSameSave(request.savePath());
        WorldMetadata metadata = saveSession.snapshot().metadata();
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(saveSession, progress)
        );
        RockCatalog catalog = RockCatalog.from(
                saveSession.snapshot().blockRegistry()
        );
        if (catalog.rocks().isEmpty()) {
            throw new IllegalArgumentException(
                    "No natural rock blocks (rock-*) were discovered in the save registry"
            );
        }

        int minY = request.mode() == RockMapMode.AT_Y
                ? request.y().orElseThrow()
                : request.minY().orElse(0);
        int maxYExclusive = request.mode() == RockMapMode.AT_Y
                ? Math.addExact(minY, 1)
                : request.maxYExclusive().orElse(metadata.mapSizeY());
        if (minY < 0 || maxYExclusive > metadata.mapSizeY()
                || minY >= maxYExclusive) {
            throw new IllegalArgumentException("Rock Y range is outside the world vertical range");
        }

        if (retainRockMap
                && request.mode() == RockMapMode.UPPER_ROCK
                && minY == 0
                && maxYExclusive == metadata.mapSizeY()
                && snapshotReader.isPresent()) {
            progress.start("Reading geology from world snapshot");
            Optional<RockMap> snapshotMap = snapshotReader.orElseThrow().read(
                    request.savePath(),
                    metadata,
                    saveSession.snapshot().blockRegistry(),
                    center,
                    request.radius()
            );
            if (snapshotMap.isPresent()) {
                RockMap map = snapshotMap.orElseThrow();
                progress.start("Rendering geology map");
                RockMapRenderResult rendered = renderer.render(map);
                progress.done("Rendered geology from world snapshot");
                return new RenderRockMapResult(
                        Optional.of(map),
                        rendered,
                        catalog,
                        new SelectiveChunkStreamStats(
                                0, 0, 0, 0, 0, 0, 0, 0
                        ),
                        new ReadDiagnostics(),
                        center,
                        minY,
                        maxYExclusive
                );
            }
        }

        List<ChunkPosition> positions = positionPlanner.plan(
                metadata,
                floor(center.x()),
                floor(center.z()),
                request.radius(),
                new ActualBlockYFilter(minY, maxYExclusive - 1)
        );
        int dimension = positions.isEmpty()
                ? 0
                : positions.getFirst().dimension();
        if (positions.stream().anyMatch(position -> position.dimension() != dimension)) {
            throw new IllegalArgumentException("planned ROCK positions use multiple dimensions");
        }
        RockStreamingSession rockSession = RockStreamingSession.open(
                center,
                request.radius(),
                minY,
                maxYExclusive,
                dimension,
                request.mode(),
                catalog
        );
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        SelectiveChunkStreamStats stats = reader
                .forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        saveSession,
                        positions,
                        catalog.rockBlockIds().stream()
                                .mapToInt(Integer::intValue)
                                .toArray(),
                        diagnostics,
                        rockSession::accept,
                        progress
                );
        RockMap map = rockSession.finish();
        progress.start("Rendering geology map");
        RockMapRenderResult rendered = renderer.render(map);
        return new RenderRockMapResult(
                retainRockMap
                        ? Optional.of(map)
                        : Optional.empty(),
                rendered,
                catalog,
                stats,
                diagnostics,
                center,
                minY,
                maxYExclusive
        );
    }

    private int floor(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world center is outside the supported block range");
        }
        return (int) floored;
    }
}
