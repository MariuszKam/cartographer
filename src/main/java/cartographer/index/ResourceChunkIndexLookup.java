package cartographer.index;

import java.util.Objects;

/** Lookup state for one revision-scoped PF-2.5 resource chunk entry. */
public record ResourceChunkIndexLookup(
        Status status,
        ResourceChunkCoverageStatus coverageStatus
) {
    public enum Status {
        HIT,
        MISS,
        CORRUPT
    }

    public ResourceChunkIndexLookup {
        Objects.requireNonNull(status, "status is required");
        if (status == Status.HIT && coverageStatus == null) {
            throw new IllegalArgumentException(
                    "resource-index hit requires coverage status"
            );
        }
        if (status != Status.HIT && coverageStatus != null) {
            throw new IllegalArgumentException(
                    "resource-index miss/corrupt cannot contain coverage status"
            );
        }
    }

    public static ResourceChunkIndexLookup hit(
            ResourceChunkCoverageStatus coverageStatus
    ) {
        return new ResourceChunkIndexLookup(
                Status.HIT,
                Objects.requireNonNull(
                        coverageStatus,
                        "coverageStatus is required"
                )
        );
    }

    public static ResourceChunkIndexLookup miss() {
        return new ResourceChunkIndexLookup(Status.MISS, null);
    }

    public static ResourceChunkIndexLookup corrupt() {
        return new ResourceChunkIndexLookup(Status.CORRUPT, null);
    }

}
