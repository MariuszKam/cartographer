package cartographer.application;

import cartographer.geology.rock.RockAtYScanner;
import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockChunkCoverage;
import cartographer.geology.rock.RockColumnScanner;
import cartographer.geology.rock.RockMap;
import cartographer.geology.rock.RockMapMode;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.render.RockMapRenderResult;
import cartographer.render.RockMapRenderer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkStreamStats;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;

import java.util.ArrayList;
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
        Objects.requireNonNull(request, "rock map request is required");
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
        List<ParsedChunk> chunks = new ArrayList<>();
        List<ChunkPosition> available = new ArrayList<>();
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        SelectiveChunkStreamStats stats = reader
                .forEachChunkByPositionMatchingBlockIdsWithCoverage(
                        request.savePath(),
                        positions,
                        catalog.rockBlockIds().stream()
                                .mapToInt(Integer::intValue)
                                .toArray(),
                        diagnostics,
                        visit -> {
                            if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                                chunks.add(visit.chunk());
                                available.add(visit.position());
                            } else if (visit.status()
                                    == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
                                available.add(visit.position());
                            }
                        }
                );
        RockChunkCoverage coverage = RockChunkCoverage.fromChunkPositions(available);
        RockMap map = request.mode() == RockMapMode.AT_Y
                ? new RockAtYScanner().scan(
                        chunks,
                        catalog,
                        coverage,
                        center,
                        request.radius(),
                        minY
                )
                : new RockColumnScanner().scan(
                        chunks,
                        catalog,
                        center,
                        request.radius(),
                        minY,
                        maxYExclusive,
                        coverage
                );
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
