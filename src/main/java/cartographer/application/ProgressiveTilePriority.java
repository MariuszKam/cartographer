package cartographer.application;

/** Scheduler priority for progressive render-tile materialization. */
public enum ProgressiveTilePriority {
    VIEWPORT,
    PREFETCH,
    BOOTSTRAP,
    PLAYER_RING,
    BACKGROUND
}
