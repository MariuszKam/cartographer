package cartographer.save;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class PackedPositionRunPlanner {

    List<PackedPositionRun> plan(Collection<Long> positions) {
        Objects.requireNonNull(positions, "positions are required");
        if (positions.isEmpty()) {
            return List.of();
        }

        List<Long> sorted = positions.stream()
                .map(position -> Objects.requireNonNull(
                        position,
                        "positions cannot contain null"
                ))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<PackedPositionRun> runs = new ArrayList<>();
        long first = sorted.getFirst();
        long previous = first;
        int size = 1;

        for (int index = 1; index < sorted.size(); index++) {
            long current = sorted.get(index);
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
        return rangeStatements * 2 <= pointStatements;
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
