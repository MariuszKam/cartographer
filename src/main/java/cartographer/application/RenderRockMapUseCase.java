package cartographer.application;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.geology.rock.RockStreamingSession;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;

import java.util.List;
import java.util.Objects;

public final class RenderRockMapUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final RockMapRenderer renderer;
    private final OreChunkPositionPlanner positionPlanner =
            new OreChunkPositionPlanner();

    public RenderRockMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RockMapRenderer renderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(
                metadataReader,
                "metadata reader is required"
        );
        this.renderer = Objects.requireNonNull(renderer, "renderer is required");
    }

    public RenderRockMapResult execute(RenderRockMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public RenderRockMapResult execute(
            RenderRockMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "rock map request is required");
        Objects.requireNonNull(progress, "progress is required");
        WorldMetadata metadata = metadataReader.read(request.savePath());
        WorldPosition center = request.center().orElseGet(
                () -> reader.readPlayerPosition(request.savePath())
        );
        RockCatalog catalog = RockCatalog.from(
                reader.readBlockRegistry(request.savePath())
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
        RockStreamingSession session = RockStreamingSession.open(
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
                        request.savePath(),
                        positions,
                        catalog.rockBlockIds().stream()
                                .mapToInt(Integer::intValue)
                                .toArray(),
                        diagnostics,
                        session::accept,
                        progress
                );
        RockMap map = session.finish();
        progress.start("Rendering geology map");
        RockMapRenderResult rendered = renderer.render(map);
        return new RenderRockMapResult(
                map,
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
