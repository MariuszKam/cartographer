package cartographer.snapshot;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.model.BlockInfo;
import cartographer.model.ChunkPosition;
import cartographer.model.WorldMetadata;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.ResourceChunkCoverageStatus;
import cartographer.perf.ResourceChunkIndexLookup;
import cartographer.perf.ResourceIndexStore;
import cartographer.perf.ResourceOccurrence;
import cartographer.perf.WorldDataSnapshot;
import cartographer.prospecting.ActualOreObservation;
import cartographer.scanner.ActualBlockMap;
import cartographer.scanner.ActualBlockMatchMode;
import cartographer.scanner.ActualBlockMatchSpec;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.MultiActualBlockMapScanner;
import cartographer.scanner.OreCodeMatcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * PF-2.6 consumer for the PF-2.5 actual-resource index.
 *
 * <p>Only ORE_CODE semantics are supported. Any missing/corrupt snapshot
 * coverage or failed indexed source position returns Optional.empty(), which
 * tells the caller to use the authoritative source path.</p>
 */
public final class SnapshotResourceReader {
    private final RenderDataCacheStore cacheStore;
    private final OreChunkPositionPlanner planner = new OreChunkPositionPlanner();
    private final MultiActualBlockMapScanner scanner =
            new MultiActualBlockMapScanner();

    public SnapshotResourceReader(RenderDataCacheStore cacheStore) {
        this.cacheStore = Objects.requireNonNull(
                cacheStore,
                "cacheStore is required"
        );
    }

    public Optional<List<ActualBlockMap>> readMaps(
            Path savePath,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            List<ActualBlockMatchSpec> specs,
            ActualBlockYFilter yFilter
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        Objects.requireNonNull(specs, "specs are required");
        Objects.requireNonNull(yFilter, "yFilter is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (specs.stream().anyMatch(
                spec -> spec == null
                        || spec.mode() != ActualBlockMatchMode.ORE_CODE
        )) {
            return Optional.empty();
        }

        MultiActualBlockMapScanner.StreamingSession session = scanner.begin(
                registry,
                centerWorldX,
                centerWorldZ,
                radius,
                specs,
                yFilter
        );
        int[] wantedBlockIds = session.wantedBlockIds();
        if (wantedBlockIds.length == 0) {
            return Optional.of(session.finish());
        }

        Optional<ResourceIndexStore> store = compatibleStore(
                savePath,
                registry
        );
        if (store.isEmpty()) {
            return Optional.empty();
        }
        List<ChunkPosition> positions = planner.plan(
                metadata,
                centerWorldX,
                centerWorldZ,
                radius,
                yFilter
        );

        try {
            Map<ChunkPosition, ResourceChunkIndexLookup> coverage =
                    store.orElseThrow().readCoverage(positions);
            if (!usableCoverage(positions, coverage)) {
                return Optional.empty();
            }
            List<Integer> wanted = Arrays.stream(wantedBlockIds)
                    .boxed()
                    .toList();
            List<ResourceOccurrence> occurrences =
                    store.orElseThrow().readOccurrences(
                            positions,
                            wanted
                    );
            for (ResourceOccurrence occurrence : occurrences) {
                session.acceptIndexedOccurrence(
                        occurrence.position(),
                        occurrence.blockId(),
                        occurrence.localX(),
                        occurrence.localZ(),
                        occurrence.localYMask()
                );
            }
            return Optional.of(session.finish());
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, ActualOreObservation>> readObservations(
            Path savePath,
            WorldMetadata metadata,
            Map<Integer, BlockInfo> registry,
            int centerWorldX,
            int centerWorldZ,
            int radius,
            List<String> resources
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(registry, "registry is required");
        resources = List.copyOf(
                Objects.requireNonNull(resources, "resources are required")
        );
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (resources.stream().anyMatch(
                resource -> resource == null || resource.isBlank()
        )) {
            throw new IllegalArgumentException(
                    "resources must not contain blanks"
            );
        }

        Optional<ResourceIndexStore> store = compatibleStore(
                savePath,
                registry
        );
        if (store.isEmpty()) {
            return Optional.empty();
        }

        List<ChunkPosition> positions = planner.plan(
                metadata,
                centerWorldX,
                centerWorldZ,
                radius,
                ActualBlockYFilter.unbounded()
        );

        try {
            Map<ChunkPosition, ResourceChunkIndexLookup> coverage =
                    store.orElseThrow().readCoverage(positions);
            if (!usableCoverage(positions, coverage)) {
                return Optional.empty();
            }
            boolean unavailable = positions.stream()
                    .map(coverage::get)
                    .filter(Objects::nonNull)
                    .map(ResourceChunkIndexLookup::coverageStatus)
                    .anyMatch(
                            status -> status
                                    == ResourceChunkCoverageStatus.MISSING
                    );

            Map<Integer, String> indexedCatalog =
                    store.orElseThrow().blockCatalog();
            Map<String, Set<Integer>> idsByResource =
                    new LinkedHashMap<>();
            LinkedHashSet<Integer> union = new LinkedHashSet<>();
            for (String resource : resources) {
                LinkedHashSet<Integer> ids = new LinkedHashSet<>();
                for (Map.Entry<Integer, String> entry :
                        indexedCatalog.entrySet()) {
                    if (OreCodeMatcher.matchesOreCode(
                            entry.getValue(),
                            resource
                    )) {
                        ids.add(entry.getKey());
                        union.add(entry.getKey());
                    }
                }
                idsByResource.put(resource, Set.copyOf(ids));
            }

            Set<Integer> observedIds = new LinkedHashSet<>();
            if (!union.isEmpty()) {
                long radiusSquared = (long) radius * radius;
                for (ResourceOccurrence occurrence :
                        store.orElseThrow().readOccurrences(
                                positions,
                                union
                        )) {
                    int worldX = Math.addExact(
                            Math.multiplyExact(
                                    occurrence.position().x(),
                                    32
                            ),
                            occurrence.localX()
                    );
                    int worldZ = Math.addExact(
                            Math.multiplyExact(
                                    occurrence.position().z(),
                                    32
                            ),
                            occurrence.localZ()
                    );
                    long dx = (long) worldX - centerWorldX;
                    long dz = (long) worldZ - centerWorldZ;
                    if (dx * dx + dz * dz <= radiusSquared) {
                        observedIds.add(occurrence.blockId());
                    }
                }
            }

            LinkedHashMap<String, ActualOreObservation> result =
                    new LinkedHashMap<>();
            for (String resource : resources) {
                boolean observed = idsByResource.getOrDefault(
                                resource,
                                Set.of()
                        ).stream()
                        .anyMatch(observedIds::contains);
                result.put(
                        resource,
                        observed
                                ? ActualOreObservation.OBSERVED
                                : unavailable
                                ? ActualOreObservation.UNAVAILABLE
                                : ActualOreObservation.NOT_OBSERVED
                );
            }
            return Optional.of(Map.copyOf(result));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    private Optional<ResourceIndexStore> compatibleStore(
            Path savePath,
            Map<Integer, BlockInfo> registry
    ) {
        try {
            Optional<WorldDataSnapshot> snapshot =
                    WorldDataSnapshot.openOrCreate(cacheStore, savePath);
            if (snapshot.isEmpty()) {
                return Optional.empty();
            }
            ResourceIndexStore store =
                    snapshot.orElseThrow().resourceIndexStore();
            if (!store.blockCatalog().equals(expectedOreCatalog(registry))) {
                return Optional.empty();
            }
            return Optional.of(store);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    private Map<Integer, String> expectedOreCatalog(
            Map<Integer, BlockInfo> registry
    ) {
        LinkedHashMap<Integer, String> expected = new LinkedHashMap<>();
        registry.values().stream()
                .filter(Objects::nonNull)
                .filter(block -> block.code() != null)
                .filter(block -> OreCodeMatcher.isOreCode(block.code()))
                .sorted(java.util.Comparator.comparingInt(BlockInfo::id))
                .forEach(block -> expected.put(block.id(), block.code()));
        return Map.copyOf(expected);
    }

    private boolean usableCoverage(
            Collection<ChunkPosition> positions,
            Map<ChunkPosition, ResourceChunkIndexLookup> coverage
    ) {
        for (ChunkPosition position : positions) {
            ResourceChunkIndexLookup lookup = coverage.get(position);
            if (lookup == null
                    || lookup.status()
                    != ResourceChunkIndexLookup.Status.HIT
                    || lookup.coverageStatus()
                    == ResourceChunkCoverageStatus.FAILED) {
                return false;
            }
        }
        return true;
    }
}
