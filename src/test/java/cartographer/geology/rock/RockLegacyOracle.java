package cartographer.geology.rock;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Test-only normalization of the current legacy ROCK result. */
public final class RockLegacyOracle {
    private RockLegacyOracle() {
    }

    public static Snapshot snapshot(RockMap map) {
        Objects.requireNonNull(map, "map is required");
        List<Cell> cells = map.columns().stream()
                .sorted(Comparator.comparingInt(RockColumnSample::worldZ)
                        .thenComparingInt(RockColumnSample::worldX))
                .map(RockLegacyOracle::cell)
                .toList();
        return new Snapshot(
                cells,
                map.observedCount(),
                map.noRockCount(),
                map.unavailableCount()
        );
    }

    private static Cell cell(RockColumnSample sample) {
        RockIdentity identity = sample.rock().orElse(null);
        Integer y = sample.rockY().isPresent()
                ? sample.rockY().getAsInt()
                : null;
        return new Cell(
                sample.worldX(),
                sample.worldZ(),
                sample.state(),
                identity == null ? null : identity.code(),
                y
        );
    }

    public record Snapshot(
            List<Cell> cells,
            long observedCount,
            long noRockCount,
            long unavailableCount
    ) {
        public Snapshot {
            cells = List.copyOf(Objects.requireNonNull(cells, "cells are required"));
        }
    }

    public record Cell(
            int worldX,
            int worldZ,
            RockColumnState state,
            String rockCode,
            Integer rockY
    ) {
        public Cell {
            Objects.requireNonNull(state, "state is required");
            if (state == RockColumnState.OBSERVED
                    && (rockCode == null || rockY == null)) {
                throw new IllegalArgumentException(
                        "observed cells require rock identity and Y"
                );
            }
            if (state != RockColumnState.OBSERVED
                    && (rockCode != null || rockY != null)) {
                throw new IllegalArgumentException(
                        "only observed cells may contain rock data"
                );
            }
        }
    }
}
