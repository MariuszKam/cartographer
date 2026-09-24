package cartographer.resource;

import cartographer.model.BlockInfo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SurfaceObjectCandidateCatalogBuilder {
    private final SurfaceObjectClassifier classifier =
            new SurfaceObjectClassifier();

    public SurfaceObjectCandidateCatalogBuilder() {
    }

    public SurfaceObjectCandidateCatalog build(Map<Integer, BlockInfo> registry) {
        Objects.requireNonNull(registry, "registry is required");
        Map<String, Aggregate> aggregates = new TreeMap<>();

        registry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(entry -> {
                    if (entry.getKey() == null) {
                        return;
                    }
                    classifier.classify(entry.getValue()).ifPresent(identity ->
                            aggregates.computeIfAbsent(
                                    identity.qualifiedResourceKey(),
                                    ignored -> new Aggregate(identity)
                            ).add(entry.getKey(), identity)
                    );
                });

        List<SurfaceObjectCandidate> candidates = aggregates.values().stream()
                .map(Aggregate::toCandidate)
                .toList();
        return new SurfaceObjectCandidateCatalog(candidates);
    }

    private static final class Aggregate {
        private final String namespace;
        private final String resourceKey;
        private final String displayName;
        private final Set<SurfaceObjectFamily> families = new HashSet<>();
        private final Set<Integer> blockIds = new HashSet<>();

        private Aggregate(SurfaceObjectIdentity identity) {
            namespace = identity.namespace();
            resourceKey = identity.resourceKey();
            displayName = identity.displayName();
        }

        private void add(int blockId, SurfaceObjectIdentity identity) {
            families.add(identity.family());
            blockIds.add(blockId);
        }

        private SurfaceObjectCandidate toCandidate() {
            return new SurfaceObjectCandidate(
                    namespace,
                    resourceKey,
                    displayName,
                    new TreeSet<>(families),
                    new TreeSet<>(blockIds)
            );
        }
    }
}
