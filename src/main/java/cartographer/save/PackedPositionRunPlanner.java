package cartographer.save;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class PackedPositionRunPlanner {

    List<PackedPositionRun> plan(Collection<Long> positions) {
        long[] sorted = sortedUnique(positions);
        return buildRuns(sorted);
    }

    Optional<List<PackedPositionRun>> planIfClearlyBetter(
            Collection<Long> positions,
            int pointBatchSize,
            int runsPerStatement
    ) {
        if (pointBatchSize <= 0 || runsPerStatement <= 0) {
            throw new IllegalArgumentException("batch sizes must be positive");
        }
        long[] sorted = sortedUnique(positions);
        if (sorted.length == 0) {
            return Optional.empty();
        }
        int runCount = countRuns(sorted);
        int pointStatements = ceilDiv(sorted.length, pointBatchSize);
        int rangeStatements = ceilDiv(runCount, runsPerStatement);
        if ((long) rangeStatements * 2L > pointStatements) {
            return Optional.empty();
        }
        return Optional.of(buildRuns(sorted));
    }

    boolean rangeStrategyClearlyBetter(
            int uniquePositions,
            List<PackedPositionRun> runs,
            int pointBatchSize,
            int runsPerStatement
    ) {
        if (uniquePositions <= 0
                || pointBatchSize <= 0
                || runsPerStatement <= 0
                || runs == null
                || runs.isEmpty()) {
            return false;
        }
        int pointStatements = ceilDiv(uniquePositions, pointBatchSize);
        int rangeStatements = ceilDiv(runs.size(), runsPerStatement);
        return (long) rangeStatements * 2L <= pointStatements;
    }

    private long[] sortedUnique(Collection<Long> positions) {
        Objects.requireNonNull(positions, "positions are required");
        if (positions.isEmpty()) {
            return new long[0];
        }
        long[] values = new long[positions.size()];
        int index = 0;
        for (Long value : positions) {
            values[index++] = Objects.requireNonNull(
                    value,
                    "positions cannot contain null"
            );
        }
        Arrays.sort(values);

        int unique = 1;
        for (int read = 1; read < values.length; read++) {
            if (values[read] != values[unique - 1]) {
                values[unique++] = values[read];
            }
        }
        return unique == values.length
                ? values
                : Arrays.copyOf(values, unique);
    }

    private int countRuns(long[] sorted) {
        if (sorted.length == 0) {
            return 0;
        }
        int runs = 1;
        long previous = sorted[0];
        for (int index = 1; index < sorted.length; index++) {
            long current = sorted[index];
            if (previous == Long.MAX_VALUE || current != previous + 1L) {
                runs++;
            }
            previous = current;
        }
        return runs;
    }

    private List<PackedPositionRun> buildRuns(long[] sorted) {
        if (sorted.length == 0) {
            return List.of();
        }
        List<PackedPositionRun> runs = new ArrayList<>();
        long first = sorted[0];
        long previous = first;
        int size = 1;

        for (int index = 1; index < sorted.length; index++) {
            long current = sorted[index];
            if (previous != Long.MAX_VALUE && current == previous + 1L) {
                previous = current;
                size++;
                continue;
            }
            runs.add(new PackedPositionRun(first, previous, size));
            first = current;
            previous = current;
            size = 1;
        }
        runs.add(new PackedPositionRun(first, previous, size));
        return List.copyOf(runs);
    }

    private int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}

record PackedPositionRun(
        long firstInclusive,
        long lastInclusive,
        int positionCount
) {
    PackedPositionRun {
        if (firstInclusive < 0 || lastInclusive < firstInclusive) {
            throw new IllegalArgumentException("invalid packed-position run");
        }
        if (positionCount <= 0) {
            throw new IllegalArgumentException("positionCount must be positive");
        }
        long expected = Math.addExact(
                Math.subtractExact(lastInclusive, firstInclusive),
                1L
        );
        if (expected != positionCount) {
            throw new IllegalArgumentException(
                    "positionCount does not match packed range"
            );
        }
    }
}
