package cartographer.geology.rock;

import cartographer.model.WorldPosition;

import java.util.Objects;

/**
 * Public streaming seam for constructing the existing compact RockMap without
 * materializing an intermediate list of column samples.
 */
public final class RockMapAssembler {
    private final RockMapBuilder delegate;
    private boolean finished;

    public RockMapAssembler(
            WorldPosition center,
            int radius,
            int minY,
            int maxYExclusive,
            RockMapMode mode,
            RockCatalog catalog
    ) {
        delegate = new RockMapBuilder(
                Objects.requireNonNull(center, "center is required"),
                radius,
                minY,
                maxYExclusive,
                Objects.requireNonNull(mode, "mode is required"),
                Objects.requireNonNull(catalog, "catalog is required")
        );
    }

    public void accept(RockColumnSample sample) {
        if (finished) {
            throw new IllegalStateException("ROCK map assembler is finished");
        }
        delegate.accept(Objects.requireNonNull(sample, "sample is required"));
    }

    public RockMap finish() {
        if (finished) {
            throw new IllegalStateException("ROCK map assembler is finished");
        }
        finished = true;
        return delegate.finish();
    }
}
