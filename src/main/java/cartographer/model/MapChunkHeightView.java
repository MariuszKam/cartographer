package cartographer.model;

/** Neutral height semantics shared by source mapchunks and compact cache tiles. */
public interface MapChunkHeightView {
    MapChunkCoordinate coordinate();

    boolean hasRainHeight();

    boolean hasEffectiveHeight();

    int effectiveHeightAt(int localX, int localZ);

    int rainHeightAt(int localX, int localZ);
}
