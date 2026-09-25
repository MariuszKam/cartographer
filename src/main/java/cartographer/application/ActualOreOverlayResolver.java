package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.model.BlockInfo;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.progress.ProgressReporter;
import cartographer.render.ActualOreOverlayResult;
import cartographer.render.ActualOreOverlaySpec;
import cartographer.resource.SnapshotResourceReader;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.MultiActualBlockMapScanner;
import cartographer.spatial.OreChunkPositionPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves actual-ore overlays from snapshot data or authoritative CHUNK rows. */
final class ActualOreOverlayResolver {
    private final VcdbsReader reader;
    private final MultiActualBlockMapScanner scanner;
    private final OreChunkPositionPlanner positionPlanner;
    private final Optional<SnapshotResourceReader> snapshotReader;

    ActualOreOverlayResolver(
            VcdbsReader reader,
            MultiActualBlockMapScanner scanner,
            OreChunkPositionPlanner positionPlanner,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.scanner = Objects.requireNonNull(scanner, "scanner is required");
        this.positionPlanner = Objects.requireNonNull(
                positionPlanner,
                "positionPlanner is required"
        );
        this.snapshotReader = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        ).map(SnapshotResourceReader::new);
    }

    Optional<List<ActualOreOverlayResult>> snapshot(
            RenderActualOreMapRequest request,
            PreparedMapData prepared,
            ProgressReporter progress
    ) {
        List<ActualOreOverlaySpec> specs = request.oreOverlays();
        if (specs.isEmpty()) {
            return Optional.of(List.of());
        }
        if (snapshotReader.isEmpty()) {
            return Optional.empty();
        }

        List<ActualBlockMatchSpec> matches = specs.stream()
                .map(spec -> new ActualBlockMatchSpec(
                        spec.match(),
                        spec.matchMode()
                ))
                .toList();
        int centerX = checkedRound(prepared.center().x());
        int centerZ = checkedRound(prepared.center().z());

        progress.start("Reading actual ore from world snapshot");
        Optional<List<ActualBlockMap>> maps =
                snapshotReader.orElseThrow().readMaps(
                        request.savePath(),
                        prepared.metadata(),
                        prepared.registry(),
                        centerX,
                        centerZ,
                        request.radius(),
                        matches,
                        request.yFilter()
                );
        if (maps.isEmpty() || maps.orElseThrow().size() != specs.size()) {
            return Optional.empty();
        }

        List<ActualOreOverlayResult> result =
                new ArrayList<>(specs.size());
        for (int index = 0; index < specs.size(); index++) {
            result.add(new ActualOreOverlayResult(
                    specs.get(index),
                    maps.orElseThrow().get(index)
            ));
        }
        return Optional.of(List.copyOf(result));
    }

    List<ActualOreOverlayResult> resolve(
            SaveSession saveSession,
            RenderActualOreMapRequest request,
            WorldPosition center,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        List<ActualOreOverlaySpec> specs = request.oreOverlays();
        if (specs.isEmpty()) {
            return List.of();
        }
        int centerX = (int) Math.round(center.x());
        int centerZ = (int) Math.round(center.z());
        List<ActualBlockMatchSpec> matches = specs.stream()
                .map(spec -> new ActualBlockMatchSpec(
                        spec.match(),
                        spec.matchMode()
                ))
                .toList();
        Optional<List<ActualBlockMap>> snapshotMaps =
                Optional.empty();
        if (snapshotReader.isPresent()) {
            progress.start("Reading actual ore from world snapshot");
            snapshotMaps = snapshotReader.orElseThrow().readMaps(
                    request.savePath(),
                    metadata,
                    registry,
                    centerX,
                    centerZ,
                    request.radius(),
                    matches,
                    request.yFilter()
            );
        }

        List<ActualBlockMap> maps;
        if (snapshotMaps.isPresent()) {
            maps = snapshotMaps.orElseThrow();
            progress.done("Actual ore loaded from world snapshot");
        } else {
            MultiActualBlockMapScanner.StreamingSession session =
                    scanner.begin(
                            registry,
                            centerX,
                            centerZ,
                            request.radius(),
                            matches,
                            request.yFilter()
                    );
            int[] wantedBlockIds = session.wantedBlockIds();
            if (wantedBlockIds.length != 0) {
                List<cartographer.model.ChunkPosition> positions =
                        positionPlanner.plan(
                                metadata,
                                centerX,
                                centerZ,
                                request.radius(),
                                request.yFilter()
                        );
                reader.forEachChunkByPositionMatchingBlockIdsAdaptive(
                        saveSession,
                        positions,
                        wantedBlockIds,
                        diagnostics,
                        session::accept,
                        progress
                );
            }
            maps = session.finish();
        }

        List<ActualOreOverlayResult> results = new ArrayList<>();
        for (int index = 0; index < specs.size(); index++) {
            results.add(new ActualOreOverlayResult(
                    specs.get(index),
                    maps.get(index)
            ));
        }
        return List.copyOf(results);
    }

    private int checkedRound(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "world center must be finite"
            );
        }
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world center is outside the supported block range"
            );
        }
        return (int) rounded;
    }
}
