package cartographer.ui;

public record PlayerPositionView(
        double x,
        double y,
        double z,
        int chunkX,
        int chunkZ
) {
}
