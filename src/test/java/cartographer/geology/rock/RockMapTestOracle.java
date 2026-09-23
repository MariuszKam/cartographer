package cartographer.geology.rock;

import java.util.List;
import java.util.Objects;

/** Test-only deterministic normalization of a ROCK result. */
final class RockMapTestOracle {
    private RockMapTestOracle() {
    }

    static Snapshot snapshot(RockMap map) {
        Objects.requireNonNull(map, "map is required");
        java.util.ArrayList<Cell> cells = new java.util.ArrayList<>();
        for (int row = 0; row < map.geometry().rowCount(); row++) {
            int worldZ = map.geometry().worldZForRow(row);
            int startX = map.geometry().rowStartX(row);
            for (int offset = 0; offset < map.geometry().rowLength(row); offset++) {
                int worldX = Math.addExact(startX, offset);
                map.sampleAt(worldX, worldZ)
                        .map(RockMapTestOracle::cell)
                        .ifPresent(cells::add);
            }
        }
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

    record Snapshot(
            List<Cell> cells,
            long observedCount,
            long noRockCount,
            long unavailableCount
    ) {
        Snapshot {
            cells = List.copyOf(Objects.requireNonNull(cells, "cells are required"));
        }
    }

    record Cell(
            int worldX,
            int worldZ,
            RockColumnState state,
            String rockCode,
            Integer rockY
    ) {
        Cell {
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
