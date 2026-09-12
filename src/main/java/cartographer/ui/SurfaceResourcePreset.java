package cartographer.ui;

import java.util.List;

public enum SurfaceResourcePreset {
    FIRE_CLAY("Fire Clay", List.of("fire", "clay")),
    CLAY("Clay", List.of("clay")),
    PEAT("Peat", List.of("peat"));

    private final String label;
    private final List<String> requiredTokens;

    SurfaceResourcePreset(String label, List<String> requiredTokens) {
        this.label = label;
        this.requiredTokens = List.copyOf(requiredTokens);
    }

    public String label() {
        return label;
    }

    public List<String> requiredTokens() {
        return requiredTokens;
    }
}
