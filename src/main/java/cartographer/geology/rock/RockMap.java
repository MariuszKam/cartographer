package cartographer.geology.rock;

import cartographer.model.WorldPosition;

import java.util.List;
import java.util.Objects;

public record RockMap(
        WorldPosition center,
        int radius,
        List<RockColumnSample> columns
) {
    public RockMap {
        Objects.requireNonNull(center, "Rock map center is required");
        if (radius <= 0) {
            throw new IllegalArgumentException("Rock map radius must be positive");
        }
        columns = List.copyOf(
                Objects.requireNonNull(columns, "Rock map columns are required")
        );
    }

    public long observedCount() {
        return columns.stream()
                .filter(column -> column.state() == RockColumnState.OBSERVED)
                .count();
    }

    public long noRockCount() {
        return columns.stream()
                .filter(column -> column.state() == RockColumnState.NO_ROCK)
                .count();
    }

    public long unavailableCount() {
        return columns.stream()
                .filter(column -> column.state() == RockColumnState.UNAVAILABLE)
                .count();
    }
}
