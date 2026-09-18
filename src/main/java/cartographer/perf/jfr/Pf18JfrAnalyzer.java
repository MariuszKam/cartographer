package cartographer.perf.jfr;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded deterministic analysis of supported JFR event families. */
public final class Pf18JfrAnalyzer {
    private static final List<String> IMPORTANT_EVENTS = List.of(
            "jdk.ExecutionSample", "jdk.ObjectAllocationSample", "jdk.GarbageCollection",
            "jdk.GCPhasePause", "jdk.FileRead", "jdk.FileWrite", "jdk.JavaMonitorEnter",
            "jdk.ThreadPark");

    public Pf18JfrSummary analyze(Path recording, Path summary,
                                  Pf18JfrCampaignIdentity identity) {
        Path normalizedRecording = recording.toAbsolutePath().normalize();
        Path normalizedSummary = summary.toAbsolutePath().normalize();
        if (Files.exists(normalizedSummary)) {
            throw new IllegalArgumentException("JFR summary already exists: " + normalizedSummary);
        }
        Pf18JfrEventAccumulator facts = new Pf18JfrEventAccumulator();
        try (RecordingFile file = new RecordingFile(normalizedRecording)) {
            Set<String> metadataTypes = file.readEventTypes().stream()
                    .map(EventType::getName).collect(Collectors.toUnmodifiableSet());
            facts.setMetadataTypes(metadataTypes);
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String name = event.getEventType().getName();
                facts.observe(name,
                        name.equals("jdk.ExecutionSample") ? firstFrame(event) : Optional.empty(),
                        name.equals("jdk.ObjectAllocationSample")
                                ? stringValue(event, "objectClass.name") : Optional.empty(),
                        name.equals("jdk.ObjectAllocationSample")
                                ? sampleWeight(event) : OptionalLong.empty(),
                        name.equals("jdk.GCPhasePause")
                                ? durationNanos(event) : OptionalLong.empty());
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
            long count = facts.counts().getOrDefault(event, 0L);
            lines.add(event + ": " + (count > 0
                    ? count + " observed"
                    : facts.metadataContains(event)
                    ? "present in recording metadata, zero observed"
                    : "UNAVAILABLE (event not represented)"));
        }
        lines.add("CPU samples are samples, not exact CPU utilization.");
        lines.add("Top CPU sample frames (bounded approximate heavy hitters):");
        appendTop(lines, facts.cpuFrames(), " samples");
        lines.add("Allocation sampled event count: " + facts.allocationEventCount());
        lines.add("Allocation sampled weight: " + allocationWeightDescription(facts));
        lines.add("Allocation evidence is sampled/estimated event weight, not exact total allocation.");
        lines.add("GC pause event count: " + facts.gcPauseEventCount());
        lines.add("GC pause duration nanoseconds: " + gcDurationDescription(facts));
        lines.add(fileIoDescription(facts));
        lines.add("Top sampled allocation classes (bounded approximate heavy hitters):");
        appendTop(lines, facts.allocationClasses(), " sampled weight");
        try {
            Files.createDirectories(normalizedSummary.getParent());
            Files.writeString(normalizedSummary, String.join("\n", lines) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new JfrProfilingException("Cannot write JFR summary", exception);
        }
        return new Pf18JfrSummary(normalizedRecording, normalizedSummary, true, identity, lines);
    }

    static String allocationWeightDescription(Pf18JfrEventAccumulator facts) {
        if (facts.allocationEventCount() == 0) return "UNAVAILABLE (no allocation sample events observed)";
        if (facts.allocationKnownWeightCount() == facts.allocationEventCount()) {
            return "available for all observed events";
        }
        return "PARTIAL; unavailable for " + (facts.allocationEventCount()
                - facts.allocationKnownWeightCount()) + " event(s)";
    }

    static String gcDurationDescription(Pf18JfrEventAccumulator facts) {
        if (facts.gcPauseEventCount() == 0) return "UNAVAILABLE (no GC pause events observed)";
        if (facts.gcPauseKnownDurationCount() == facts.gcPauseEventCount()) {
            return Long.toString(facts.gcPauseDurationNanos());
        }
        return "PARTIAL; unavailable for " + (facts.gcPauseEventCount()
                - facts.gcPauseKnownDurationCount()) + " event(s)";
    }

    static String fileIoDescription(Pf18JfrEventAccumulator facts) {
        long reads = facts.counts().getOrDefault("jdk.FileRead", 0L);
        long writes = facts.counts().getOrDefault("jdk.FileWrite", 0L);
        if (reads > 0 || writes > 0) {
            return "File I/O observed; SQLite attribution may be incomplete";
        }
        if (facts.metadataContains("jdk.FileRead") || facts.metadataContains("jdk.FileWrite")) {
            return "File I/O event types represented; zero observations";
        }
        return "File I/O evidence UNAVAILABLE / NOT DETERMINED";
    }

    private static void appendTop(List<String> lines, java.util.Map<String, Long> values,
                                  String suffix) {
        values.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry.comparingByKey()))
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue() + suffix));
        if (values.isEmpty()) lines.add("UNAVAILABLE");
    }

    private static Optional<String> firstFrame(RecordedEvent event) {
        if (event.getStackTrace() == null || event.getStackTrace().getFrames().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(event.getStackTrace().getFrames().getFirst().toString());
    }

    private static Optional<String> stringValue(RecordedEvent event, String field) {
        try {
            String value = event.getString(field);
            return value == null || value.isBlank() ? Optional.of("UNKNOWN") : Optional.of(value);
        } catch (RuntimeException ignored) {
            return Optional.of("UNKNOWN");
        }
    }

    private static OptionalLong sampleWeight(RecordedEvent event) {
        try {
            long value = event.getLong("weight");
            return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
        } catch (RuntimeException ignored) {
            return OptionalLong.empty();
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
}
