package cartographer.perf.jfr;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/** Bounded facts collected from a streaming JFR recording. */
final class Pf18JfrEventAccumulator {
    static final int CAPACITY = 10;
    private final Map<String, Long> counts = new TreeMap<>();
    private final Set<String> metadataTypes = new java.util.TreeSet<>();
    private final SpaceSaving cpuFrames = new SpaceSaving(CAPACITY);
    private final SpaceSaving allocationClasses = new SpaceSaving(CAPACITY);
    private long allocationEventCount;
    private long allocationKnownWeightCount;
    private long gcPauseEventCount;
    private long gcPauseKnownDurationCount;
    private long gcPauseDurationNanos;

    void setMetadataTypes(Set<String> types) {
        metadataTypes.clear();
        metadataTypes.addAll(types);
    }

    void observe(String eventType, Optional<String> cpuFrame, Optional<String> allocationClass,
                 OptionalLong allocationWeight, OptionalLong gcDurationNanos) {
        counts.merge(eventType, 1L, Long::sum);
        if (cpuFrame.isPresent()) cpuFrames.offer(cpuFrame.orElseThrow(), 1);
        if (eventType.equals("jdk.ObjectAllocationSample")) {
            allocationEventCount++;
            if (allocationWeight.isPresent()) {
                allocationKnownWeightCount++;
                allocationClass.ifPresent(value -> allocationClasses.offer(value,
                        allocationWeight.getAsLong()));
            }
        }
        if (eventType.equals("jdk.GCPhasePause")) {
            gcPauseEventCount++;
            if (gcDurationNanos.isPresent()) {
                gcPauseKnownDurationCount++;
                gcPauseDurationNanos = Math.addExact(gcPauseDurationNanos,
                        gcDurationNanos.getAsLong());
            }
        }
    }

    Map<String, Long> counts() {
        return Map.copyOf(counts);
    }

    boolean metadataContains(String eventType) {
        return metadataTypes.contains(eventType);
    }

    Map<String, Long> cpuFrames() {
        return cpuFrames.values();
    }

    Map<String, Long> allocationClasses() {
        return allocationClasses.values();
    }

    long allocationEventCount() {
        return allocationEventCount;
    }

    long allocationKnownWeightCount() {
        return allocationKnownWeightCount;
    }

    long gcPauseEventCount() {
        return gcPauseEventCount;
    }

    long gcPauseKnownDurationCount() {
        return gcPauseKnownDurationCount;
    }

    long gcPauseDurationNanos() {
        return gcPauseDurationNanos;
    }

    static final class SpaceSaving {
        private final int capacity;
        private final Map<String, Long> estimates = new HashMap<>();

        SpaceSaving(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
            this.capacity = capacity;
        }

        void offer(String key, long weight) {
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
            if (estimates.containsKey(key)) {
                estimates.merge(key, weight, Math::addExact);
                return;
            }
            if (estimates.size() < capacity) {
                estimates.put(key, weight);
                return;
            }
            Map.Entry<String, Long> minimum = estimates.entrySet().stream()
                    .min(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .orElseThrow();
            long replacement = Math.addExact(minimum.getValue(), weight);
            estimates.remove(minimum.getKey());
            estimates.put(key, replacement);
        }

        Map<String, Long> values() {
            return Map.copyOf(estimates);
        }
    }
}
