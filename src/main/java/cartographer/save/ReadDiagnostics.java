package cartographer.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadDiagnostics {
    private static final int SAMPLE_LIMIT = 10;
    private int parsed;
    private int skipped;
    private int failed;
    private int registryBlocks;
    private final List<String> skippedNotes = new ArrayList<>();
    private final List<String> failureSamples = new ArrayList<>();
    private final Map<String, Integer> failureReasons = new LinkedHashMap<>();
    private final List<String> generalNotes = new ArrayList<>();

    public void recordParsed() {
        parsed++;
    }

    public void recordSkipped(String reason) {
        skipped++;
        if (skippedNotes.size() < SAMPLE_LIMIT) {
            skippedNotes.add("skipped: " + reason);
        }
    }

    public void recordFailed(String reason) {
        failed++;
        failureReasons.merge(
                reason,
                1,
                Integer::sum
        );

        if (failureSamples.size() < SAMPLE_LIMIT) {
            failureSamples.add("failed: " + reason);
        }
    }

    public void missingTable(String tableName) {
        generalNotes.add("missing table: " + tableName);
    }

    public void registryBlocks(int registryBlocks) {
        this.registryBlocks = registryBlocks;
    }

    public int parsed() {
        return parsed;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public int registryBlocks() {
        return registryBlocks;
    }

    public List<String> notes() {
        List<String> notes =
                new ArrayList<>();

        notes.addAll(
                generalNotes
        );

        notes.addAll(
                skippedNotes
        );

        notes.addAll(
                failureSamples
        );

        return List.copyOf(notes);
    }

    public List<String> skippedNotes() {
        return List.copyOf(
                skippedNotes
        );
    }

    public List<String> failureSamples() {
        return List.copyOf(
                failureSamples
        );
    }

    public Map<String, Integer> failureReasons() {
        return Map.copyOf(
                failureReasons
        );
    }

    public List<String> failureReasonLines() {
        return failureReasons.entrySet()
                .stream()
                .sorted(
                        (left, right) ->
                                Integer.compare(
                                        right.getValue(),
                                        left.getValue()
                                )
                )
                .limit(SAMPLE_LIMIT)
                .map(
                        entry ->
                                entry.getValue()
                                        + " x "
                                        + entry.getKey()
                )
                .toList();
    }
}
