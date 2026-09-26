package cartographer.application;

/** Data required to materialize one progressive base render tile. */
public enum MapTileDataRequirement {
    TERRAIN(false),
    TERRAIN_AND_SURFACE(true);

    private final boolean surfaceRequired;

    MapTileDataRequirement(boolean surfaceRequired) {
        this.surfaceRequired = surfaceRequired;
    }

    public boolean surfaceRequired() {
        return surfaceRequired;
    }
}
