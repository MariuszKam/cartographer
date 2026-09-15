package cartographer.resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable registry-derived catalogue of supported surface-object candidates. */
public final class SurfaceObjectCandidateCatalog {
    private final List<SurfaceObjectCandidate> candidates;
    private final Map<Integer, SurfaceObjectCandidate> candidatesByBlockId;
    private final Map<String, SurfaceObjectCandidate> candidatesByQualifiedKey;

    SurfaceObjectCandidateCatalog(List<SurfaceObjectCandidate> candidates) {
        this.candidates = List.copyOf(candidates);

        Map<Integer, SurfaceObjectCandidate> byBlockId = new TreeMap<>();
        Map<String, SurfaceObjectCandidate> byQualifiedKey = new TreeMap<>();
        for (SurfaceObjectCandidate candidate : this.candidates) {
            byQualifiedKey.put(candidate.qualifiedResourceKey(), candidate);
            for (int blockId : candidate.blockIds()) {
                SurfaceObjectCandidate previous = byBlockId.put(blockId, candidate);
                if (previous != null && previous != candidate) {
                    throw new IllegalArgumentException(
                            "Block ID belongs to multiple surface object candidates: " + blockId
                    );
                }
            }
        }
        candidatesByBlockId = Collections.unmodifiableMap(byBlockId);
        candidatesByQualifiedKey = Collections.unmodifiableMap(byQualifiedKey);
    }

    public List<SurfaceObjectCandidate> candidates() {
        return candidates;
    }

    public SortedSet<Integer> candidateBlockIds() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(candidatesByBlockId.keySet()));
    }

    public Optional<SurfaceObjectCandidate> findByBlockId(int blockId) {
        return Optional.ofNullable(candidatesByBlockId.get(blockId));
    }

    public Optional<SurfaceObjectCandidate> findByQualifiedResourceKey(String qualifiedResourceKey) {
        if (qualifiedResourceKey == null || qualifiedResourceKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(candidatesByQualifiedKey.get(qualifiedResourceKey));
    }
}
