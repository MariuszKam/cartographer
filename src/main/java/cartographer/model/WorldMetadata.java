package cartographer.model;

public record WorldMetadata(
        int mapSizeX,
        int mapSizeY,
        int mapSizeZ
) {

    public double originX() {
        return mapSizeX / 2.0;
    }

    public double originZ() {
        return mapSizeZ / 2.0;
    }

    public DisplayPosition toDisplay(
            WorldPosition absolutePosition
    ) {
        return new DisplayPosition(
                absolutePosition.x() - originX(),
                absolutePosition.y(),
                absolutePosition.z() - originZ()
        );
    }

    public WorldPosition toAbsolute(
            DisplayPosition displayPosition
    ) {
        return new WorldPosition(
                displayPosition.x() + originX(),
                displayPosition.y(),
                displayPosition.z() + originZ()
        );
    }
}
