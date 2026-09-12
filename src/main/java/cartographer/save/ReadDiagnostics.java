package cartographer.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReadDiagnostics {

    private static final int SAMPLE_LIMIT =
            10;

    private int parsed;

    private int skipped;

    private int failed;

    private int registryBlocks;

    private int liquidDecodeFailures;

    private final Map<String, Integer> skippedReasons =
            new LinkedHashMap<>();

    private final List<String> failureSamples =
            new ArrayList<>();

    private final Map<String, Integer> failureReasons =
            new LinkedHashMap<>();

    private final List<String> liquidFailureSamples =
            new ArrayList<>();

    private final Map<String, Integer> liquidFailureReasons =
            new LinkedHashMap<>();

    private final Set<String> generalNotes =
            new LinkedHashSet<>();

    public void recordParsed() {
        parsed++;
    }

    public void recordSkipped(
            String reason
    ) {
        skipped++;

        skippedReasons.merge(
                normalizedReason(
                        reason
                ),
                1,
                Integer::sum
        );
    }

    public void recordFailed(
            String reason
    ) {
        failed++;

        String normalized =
                normalizedReason(
                        reason
                );

        failureReasons.merge(
                normalized,
                1,
                Integer::sum
        );

        if (failureSamples.size()
                < SAMPLE_LIMIT) {

            failureSamples.add(
                    "failed: "
                            + normalized
            );
        }
    }

    public void recordLiquidDecodeFailure(
            String reason
    ) {
        liquidDecodeFailures++;

        String normalized =
                normalizedReason(
                        reason
                );

        liquidFailureReasons.merge(
                normalized,
                1,
                Integer::sum
        );

        if (liquidFailureSamples.size()
                < SAMPLE_LIMIT) {

            liquidFailureSamples.add(
                    "liquid decode failed: "
                            + normalized
            );
        }
    }

    public void missingTable(
            String tableName
    ) {
        generalNotes.add(
                "missing table: "
                        + tableName
        );
    }

    public void registryBlocks(
            int registryBlocks
    ) {
        this.registryBlocks =
                registryBlocks;
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

    public int liquidDecodeFailures() {
        return liquidDecodeFailures;
    }

    public List<String> notes() {
        List<String> notes =
                new ArrayList<>();

        notes.addAll(
                generalNotes
        );

        notes.addAll(
                skippedNotes()
        );

        return List.copyOf(
                notes
        );
    }

    public List<String> skippedNotes() {
        return skippedReasons.entrySet()
                .stream()
                .sorted(
                        (left, right) -> {
                            int countComparison =
                                    Integer.compare(
                                            right.getValue(),
                                            left.getValue()
                                    );

                            if (countComparison != 0) {
                                return countComparison;
                            }

                            return left.getKey()
                                    .compareTo(
                                            right.getKey()
                                    );
                        }
                )
                .limit(
                        SAMPLE_LIMIT
                )
                .map(
                        entry ->
                                "skipped: "
                                        + entry.getValue()
                                        + " x "
                                        + entry.getKey()
                )
                .toList();
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

    public Map<String, Integer> liquidFailureReasons() {
        return Map.copyOf(
                liquidFailureReasons
        );
    }

    public List<String> failureReasonLines() {
        return failureReasons.entrySet()
                .stream()
                .sorted(
                        (left, right) -> {
                            int countComparison =
                                    Integer.compare(
                                            right.getValue(),
                                            left.getValue()
                                    );

                            if (countComparison != 0) {
                                return countComparison;
                            }

                            return left.getKey()
                                    .compareTo(
                                            right.getKey()
                                    );
                        }
                )
                .limit(
                        SAMPLE_LIMIT
                )
                .map(
                        entry ->
                                entry.getValue()
                                        + " x "
                                        + entry.getKey()
                )
                .toList();
    }

    public List<String> liquidFailureReasonLines() {
        return liquidFailureReasons.entrySet()
                .stream()
                .sorted(
                        (left, right) -> {
                            int countComparison =
                                    Integer.compare(
                                            right.getValue(),
                                            left.getValue()
                                    );

                            if (countComparison != 0) {
                                return countComparison;
                            }

                            return left.getKey()
                                    .compareTo(
                                            right.getKey()
                                    );
                        }
                )
                .limit(
                        SAMPLE_LIMIT
                )
                .map(
                        entry ->
                                entry.getValue()
                                        + " x "
                                        + entry.getKey()
                )
                .toList();
    }

    private String normalizedReason(
            String reason
    ) {
        if (reason == null
                || reason.isBlank()) {

            return "unspecified";
        }

        return reason.trim();
    }
}