package cartographer.index;

/**
 * Authoritative source-coverage state recorded for one main-world server chunk
 * by the PF-2.5 resource index.
 */
public enum ResourceChunkCoverageStatus {
    AVAILABLE,
    MISSING,
    FAILED
}
