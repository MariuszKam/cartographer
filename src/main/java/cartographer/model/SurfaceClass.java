package cartographer.model;

public enum SurfaceClass {
    WATER("Water"),
    GRASS("Grass"),
    FOREST_FLOOR("Forest floor"),
    SOIL("Soil"),
    ROCK("Rock"),
    SAND("Sand"),
    GRAVEL("Gravel"),
    VEGETATION("Vegetation"),
    SNOW("Snow"),
    UNKNOWN("Unknown");

    private final String label;

    SurfaceClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}