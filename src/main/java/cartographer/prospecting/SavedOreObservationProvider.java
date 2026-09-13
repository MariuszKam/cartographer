package cartographer.prospecting;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SelectiveChunkVisitStatus;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.MultiActualBlockMapScanner;
import cartographer.scanner.OreCodeMatcher;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SavedOreObservationProvider implements ActualOreObservationProvider {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final OreChunkPositionPlanner positionPlanner = new OreChunkPositionPlanner();
    private final MultiActualBlockMapScanner scanner = new MultiActualBlockMapScanner();

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(
                metadataReader,
                "metadata reader is required"
        );
    }

    @Override
    public boolean observed(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    ) {
        return observation(resourceKey, savePath, center, radius)
                == ActualOreObservation.OBSERVED;
    }

    @Override
    public ActualOreObservation observation(
            String resourceKey,
            Path savePath,
            WorldPosition center,
            int radius
    ) {
        Map<Integer, BlockInfo> registry = reader.readBlockRegistry(savePath);
        int[] wantedBlockIds = registry.values().stream()
                .filter(Objects::nonNull)
                .filter(block -> OreCodeMatcher.matchesOreCode(block.code(), resourceKey))
                .mapToInt(BlockInfo::id)
                .distinct()
                .toArray();
        if (wantedBlockIds.length == 0) {
            return ActualOreObservation.NOT_OBSERVED;
        }

        WorldMetadata metadata = metadataReader.read(savePath);
        int centerX = floor(center.x());
        int centerZ = floor(center.z());
        List<ChunkPosition> positions = positionPlanner.plan(
                metadata,
                centerX,
                centerZ,
                radius,
                ActualBlockYFilter.unbounded()
        );
        if (positions.isEmpty()) {
            return ActualOreObservation.NOT_OBSERVED;
        }

        MultiActualBlockMapScanner.StreamingSession session = scanner.begin(
                registry,
                centerX,
                centerZ,
                radius,
                List.of(new ActualBlockMatchSpec(resourceKey, ActualBlockMatchMode.ORE_CODE)),
                ActualBlockYFilter.unbounded()
        );
        AtomicBoolean unavailable = new AtomicBoolean();
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        reader.forEachChunkByPositionMatchingBlockIdsWithCoverage(
                savePath,
                positions,
                wantedBlockIds,
                diagnostics,
                visit -> {
                    if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                        session.accept(visit.chunk());
                    } else if (visit.status() == SelectiveChunkVisitStatus.FAILED
                            || visit.status() == SelectiveChunkVisitStatus.MISSING) {
                        unavailable.set(true);
                    }
                }
        );
        // The coverage-aware reader reports failures through diagnostics; a failed
        // or missing requested row means the absence of ore cannot be conclusive.
        unavailable.set(
                unavailable.get()
                        || diagnostics.failed() > 0
                        || diagnostics.skipped() > 0
        );
        long matchingBlocks = session.finish().getFirst().matchingBlocks();
        if (matchingBlocks > 0) {
            return ActualOreObservation.OBSERVED;
        }
        return unavailable.get()
                ? ActualOreObservation.UNAVAILABLE
                : ActualOreObservation.NOT_OBSERVED;
    }

    private int floor(double value) {
        double result = Math.floor(value);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world center is outside the supported block range");
        }
        return (int) result;
    }
}
