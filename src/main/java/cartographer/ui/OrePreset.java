package cartographer.ui;

public enum OrePreset {
    NATIVE_COPPER("Native Copper", "nativecopper"),
    TIN_CASSITERITE("Tin / Cassiterite", "cassiterite");

    private final String label;
    private final String match;

    OrePreset(String label, String match) {
        this.label = label;
        this.match = match;
    }

    public String label() {
        return label;
    }

    public String match() {
        return match;
    }

    @Override
    public String toString() {
        return label;
    }
}
