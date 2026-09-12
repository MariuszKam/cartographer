package cartographer.model;

public record MapRegionCoordinate(
        int x,
        int z
) {
    public static final int SIZE_MAP_CHUNKS =
            16;
}