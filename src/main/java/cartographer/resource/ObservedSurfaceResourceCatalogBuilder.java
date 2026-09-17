package cartographer.resource;

import cartographer.model.SurfaceBlock;
import cartographer.scanner.SurfaceObjectCompactScanResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds observed logical resources from scanner blocks and registry candidates. */
public final class ObservedSurfaceResourceCatalogBuilder {
    private static final Comparator<SurfaceObjectObservation> OBSERVATION_ORDER =
            Comparator.comparingInt(SurfaceObjectObservation::worldZ)
                    .thenComparingInt(SurfaceObjectObservation::worldX)
                    .thenComparingInt(SurfaceObjectObservation::worldY)
                    .thenComparingInt(SurfaceObjectObservation::blockId);

    public ObservedSurfaceResourceCatalog build(
            SurfaceObjectCandidateCatalog candidateCatalog,
            List<SurfaceBlock> blocks
    ) {
        Objects.requireNonNull(candidateCatalog, "candidate catalog is required");
        Objects.requireNonNull(blocks, "surface blocks are required");

        Map<String, List<SurfaceObjectObservation>> grouped = new HashMap<>();
        for (SurfaceBlock block : blocks) {
            if (block == null || block.blockInfo() == null) {
                continue;
            }
            candidateCatalog.findByBlockId(block.blockInfo().id()).ifPresent(
                    candidate -> grouped.computeIfAbsent(
                            candidate.qualifiedResourceKey(),
                            ignored -> new ArrayList<>()
                    ).add(new SurfaceObjectObservation(
                            candidate,
                            block.worldX(),
                            block.y(),
                            block.worldZ(),
                            block.blockInfo().id()
                    ))
            );
        }

        List<ObservedSurfaceResource> resources = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<SurfaceObjectObservation> observations = entry.getValue();
                    observations.sort(OBSERVATION_ORDER);
                    SurfaceObjectCandidate candidate = observations.getFirst().candidate();
                    return new ObservedSurfaceResource(candidate, observations);
                })
                .toList();
        return new ObservedSurfaceResourceCatalog(resources);
    }

    /** Builds directly from primitive streaming observations; no SurfaceBlock adapter. */
    public ObservedSurfaceResourceCatalog build(
            SurfaceObjectCandidateCatalog candidateCatalog,
            SurfaceObjectCompactScanResult scan
    ) {
        Objects.requireNonNull(candidateCatalog, "candidate catalog is required");
        Objects.requireNonNull(scan, "compact scan is required");
        Map<String, List<SurfaceObjectObservation>> grouped = new HashMap<>();
        scan.forEachObservation((worldX, worldY, worldZ, blockId) ->
                candidateCatalog.findByBlockId(blockId).ifPresent(candidate ->
                        grouped.computeIfAbsent(
                                candidate.qualifiedResourceKey(), ignored -> new ArrayList<>())
                                .add(new SurfaceObjectObservation(
                                        candidate, worldX, worldY, worldZ, blockId))));
        return buildGrouped(grouped);
    }

    private ObservedSurfaceResourceCatalog buildGrouped(
            Map<String, List<SurfaceObjectObservation>> grouped
    ) {
        List<ObservedSurfaceResource> resources = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<SurfaceObjectObservation> observations = entry.getValue();
                    observations.sort(OBSERVATION_ORDER);
                    SurfaceObjectCandidate candidate = observations.getFirst().candidate();
                    return new ObservedSurfaceResource(candidate, observations);
                })
                .toList();
        return new ObservedSurfaceResourceCatalog(resources);
    }
}
