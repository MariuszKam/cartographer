package cartographer.perf.jfr;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bounded deterministic analysis of supported JFR event families. */
public final class Pf18JfrAnalyzer {
    private static final List<String> IMPORTANT_EVENTS = List.of(
            "jdk.ExecutionSample", "jdk.ObjectAllocationSample", "jdk.GarbageCollection",
            "jdk.GCPhasePause", "jdk.FileRead", "jdk.FileWrite", "jdk.JavaMonitorEnter",
            "jdk.ThreadPark");
    private static final int MAX_TOP_FRAMES = 10;

    public Pf18JfrSummary analyze(Path recording, Path summary) {
        Path normalizedRecording = recording.toAbsolutePath().normalize();
        Path normalizedSummary = summary.toAbsolutePath().normalize();
        if (Files.exists(normalizedSummary)) {
            throw new IllegalArgumentException("JFR summary already exists: " + normalizedSummary);
        }
        Map<String, Long> counts = new TreeMap<>();
        Map<String, Long> allocationWeights = new HashMap<>();
        Map<String, Long> cpuFrames = new HashMap<>();
        long gcPauseNanoseconds = 0;
        try (RecordingFile file = new RecordingFile(normalizedRecording)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String name = event.getEventType().getName();
                counts.merge(name, 1L, Long::sum);
                if (name.equals("jdk.ExecutionSample") && event.getStackTrace() != null
                        && !event.getStackTrace().getFrames().isEmpty()) {
                    String frame = event.getStackTrace().getFrames().getFirst().toString();
                    if (cpuFrames.size() < MAX_TOP_FRAMES || cpuFrames.containsKey(frame)) {
                        cpuFrames.merge(frame, 1L, Long::sum);
                    }
                }
                if (name.equals("jdk.GCPhasePause")) {
                    gcPauseNanoseconds += durationNanos(event);
                }
                if (name.equals("jdk.ObjectAllocationSample")) {
                    String type = stringOrUnknown(event, "objectClass.name");
                    long weight = valueOrOne(event, "weight");
                    if (allocationWeights.size() < MAX_TOP_FRAMES
                            || allocationWeights.containsKey(type)) {
                        allocationWeights.merge(type, weight, Long::sum);
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
        lines.add("Event evidence:");
        for (String event : IMPORTANT_EVENTS) {
            long count = counts.getOrDefault(event, 0L);
            lines.add(event + ": " + (counts.containsKey(event)
                    ? count + " observed"
                    : "UNAVAILABLE (event not present)"));
        }
        lines.add("CPU samples are samples, not exact CPU utilization.");
        lines.add("Top CPU sample frames:");
        cpuFrames.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TOP_FRAMES)
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue()
                        + " samples"));
        if (cpuFrames.isEmpty()) lines.add("UNAVAILABLE");
        lines.add("Allocation evidence is sampled/estimated event weight, not exact total allocation.");
        lines.add("GC pause duration nanoseconds: " + (counts.containsKey("jdk.GCPhasePause")
                ? Long.toString(gcPauseNanoseconds) : "UNAVAILABLE"));
        lines.add("File I/O observed; SQLite attribution may be incomplete.");
        lines.add("Top sampled allocation classes:");
        allocationWeights.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TOP_FRAMES)
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getValue()
                        + " sampled weight"));
        if (allocationWeights.isEmpty()) lines.add("UNAVAILABLE");
        try {
            Files.createDirectories(normalizedSummary.getParent());
            Files.writeString(normalizedSummary, String.join("\n", lines) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new JfrProfilingException("Cannot write JFR summary", exception);
        }
        return new Pf18JfrSummary(normalizedRecording, normalizedSummary, true, lines);
    }

    private static long valueOrOne(RecordedEvent event, String field) {
        try {
            long value = event.getLong(field);
            return value < 0 ? 1 : value;
        } catch (RuntimeException ignored) {
            return 1;
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

    private static long durationNanos(RecordedEvent event) {
        try {
            long nanos = event.getDuration("duration").toNanos();
            return nanos < 0 ? 0 : nanos;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
