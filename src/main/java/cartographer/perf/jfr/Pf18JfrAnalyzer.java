package cartographer.perf.jfr;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Bounded deterministic analysis of supported JFR event families. */
public final class Pf18JfrAnalyzer {
    private static final List<String> IMPORTANT_EVENTS = List.of(
            "jdk.ExecutionSample", "jdk.ObjectAllocationSample", "jdk.GarbageCollection",
            "jdk.GCPhasePause", "jdk.FileRead", "jdk.FileWrite", "jdk.JavaMonitorEnter",
            "jdk.ThreadPark");
    private static final int MAX_TOP_FRAMES = 10;

    public Pf18JfrSummary analyze(Path recording, Path summary) {
        return analyze(recording, summary, new Pf18JfrCampaignIdentity(
                "unknown", "unknown", "unknown", 1, "UNAVAILABLE", "profile", 1,
                recording, "UNAVAILABLE", java.util.Optional.empty(), java.util.Optional.empty()));
    }

    public Pf18JfrSummary analyze(Path recording, Path summary,
                                  Pf18JfrCampaignIdentity identity) {
        Path normalizedRecording = recording.toAbsolutePath().normalize();
        Path normalizedSummary = summary.toAbsolutePath().normalize();
        if (Files.exists(normalizedSummary)) {
            throw new IllegalArgumentException("JFR summary already exists: " + normalizedSummary);
        }
        Map<String, Long> counts = new TreeMap<>();
        Map<String, Long> allocationWeights = new HashMap<>();
        Map<String, Long> cpuFrames = new HashMap<>();
        long gcPauseNanoseconds = 0;
        long gcPauseEventCount = 0;
        long gcPauseDurationKnownCount = 0;
        long allocationEventCount = 0;
        long allocationWeightKnownCount = 0;
        Set<String> metadataTypes;
        try {
            metadataTypes = RecordingFile.readEventTypes(normalizedRecording).stream()
                    .map(EventType::getName).collect(Collectors.toUnmodifiableSet());
            try (RecordingFile file = new RecordingFile(normalizedRecording)) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    String name = event.getEventType().getName();
                    counts.merge(name, 1L, Long::sum);
                    if (name.equals("jdk.ExecutionSample") && event.getStackTrace() != null
                            && !event.getStackTrace().getFrames().isEmpty()) {
                        offerHeavyHitter(cpuFrames,
                                event.getStackTrace().getFrames().getFirst().toString(), 1);
                    }
                    if (name.equals("jdk.GCPhasePause")) {
                        gcPauseEventCount++;
                        OptionalLong duration = durationNanos(event);
                        if (duration.isPresent()) {
                            gcPauseDurationKnownCount++;
                            gcPauseNanoseconds = Math.addExact(
                                    gcPauseNanoseconds, duration.getAsLong());
                        }
                    }
                    if (name.equals("jdk.ObjectAllocationSample")) {
                        allocationEventCount++;
                        OptionalLong weight = sampleWeight(event);
                        if (weight.isPresent()) {
                            allocationWeightKnownCount++;
                            offerHeavyHitter(allocationWeights,
                                    stringOrUnknown(event, "objectClass.name"), weight.getAsLong());
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new JfrProfilingException("Cannot analyze JFR recording", exception);
        }

        List<String> lines = new ArrayList<>();
        lines.add("PF-1.8 JFR diagnostic/profiling summary");
        lines.add("Recording: " + normalizedRecording);
        lines.add("Diagnostic/profiling run — not timing baseline");
        lines.add("Git SHA: " + identity.gitSha());
        lines.add("Workload: " + identity.workloadId());
        lines.add("Family: " + identity.workloadFamily());
        lines.add("Radius: R" + identity.radius());
        lines.add("Declared profile state: " + identity.declaredProfileState());
        lines.add("JFR configuration: " + identity.jfrConfiguration());
        lines.add("Max recording size bytes: " + identity.maxRecordingSizeBytes());
        lines.add("Source path: " + identity.sourcePath());
        lines.add("Source safety: " + identity.sourceSafetyStatus());
        lines.add("Semantic fingerprint: " + identity.semanticFingerprint().orElse("UNAVAILABLE"));
        lines.add("Image fingerprint: " + identity.imageFingerprint().orElse("UNAVAILABLE"));
        lines.add("Event evidence:");
        for (String event : IMPORTANT_EVENTS) {
            long count = counts.getOrDefault(event, 0L);
            lines.add(event + ": " + (counts.containsKey(event)
                    ? count + " observed"
                    : metadataTypes.contains(event)
                    ? "present in recording metadata, zero observed"
                    : "UNAVAILABLE (event not represented)"));
        }
        lines.add("CPU samples are samples, not exact CPU utilization.");
        lines.add("Top CPU sample frames (bounded approximate heavy hitters):");
        appendTop(lines, cpuFrames, " samples");
        lines.add("Allocation sampled event count: " + allocationEventCount);
        lines.add("Allocation sampled weight: " + (allocationEventCount == 0
                ? "UNAVAILABLE (no allocation sample events observed)"
                : allocationWeightKnownCount == allocationEventCount
                ? "available for observed events" : "UNAVAILABLE for "
                + (allocationEventCount - allocationWeightKnownCount) + " event(s)"));
        lines.add("Allocation evidence is sampled/estimated event weight, not exact total allocation.");
        lines.add("GC pause event count: " + gcPauseEventCount);
        lines.add("GC pause duration nanoseconds: " + (gcPauseEventCount == 0
                ? "UNAVAILABLE (no GC pause events observed)"
                : gcPauseDurationKnownCount == gcPauseEventCount
                ? Long.toString(gcPauseNanoseconds) : "UNAVAILABLE for "
                + (gcPauseEventCount - gcPauseDurationKnownCount) + " event(s)"));
        lines.add("File I/O observed; SQLite attribution may be incomplete.");
        lines.add("Top sampled allocation classes (bounded approximate heavy hitters):");
        appendTop(lines, allocationWeights, " sampled weight");
        try {
            Files.createDirectories(normalizedSummary.getParent());
            Files.writeString(normalizedSummary, String.join("\n", lines) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new JfrProfilingException("Cannot write JFR summary", exception);
        }
        return new Pf18JfrSummary(normalizedRecording, normalizedSummary, true, identity, lines);
    }

    private static void appendTop(List<String> lines, Map<String, Long> values, String suffix) {
        values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TOP_FRAMES)
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue() + suffix));
        if (values.isEmpty()) lines.add("UNAVAILABLE");
    }

    private static OptionalLong sampleWeight(RecordedEvent event) {
        try {
            long value = event.getLong("weight");
            return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
        } catch (RuntimeException ignored) {
            return OptionalLong.empty();
        }
    }

    private static String stringOrUnknown(RecordedEvent event, String field) {
        try {
            String value = event.getString(field);
            return value == null || value.isBlank() ? "UNKNOWN" : value;
        } catch (RuntimeException ignored) {
            return "UNKNOWN";
        }
    }

    private static OptionalLong durationNanos(RecordedEvent event) {
        try {
            long nanos = event.getDuration("duration").toNanos();
            return nanos < 0 ? OptionalLong.empty() : OptionalLong.of(nanos);
        } catch (RuntimeException ignored) {
            return OptionalLong.empty();
        }
    }

    static void offerHeavyHitter(Map<String, Long> values, String key, long weight) {
        if (values.containsKey(key)) {
            values.merge(key, weight, Long::sum);
            return;
        }
        if (values.size() < MAX_TOP_FRAMES) {
            values.put(key, weight);
            return;
        }
        Map.Entry<String, Long> minimum = values.entrySet().stream()
                .min(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow();
        if (weight > minimum.getValue()
                || (weight == minimum.getValue() && key.compareTo(minimum.getKey()) < 0)) {
            values.remove(minimum.getKey());
            values.put(key, weight);
        }
    }
}
