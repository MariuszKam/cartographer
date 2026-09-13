package cartographer.ui;

import java.util.List;

public enum SurfaceResourcePreset {
    FIRE_CLAY("Fire Clay", List.of("fire", "clay")),
    CLAY("Clay", List.of("clay")),
    PEAT("Peat", List.of("peat")),
    OBSIDIAN("Obsidian", List.of(), List.of(
            "loosestones-obsidian-",
            "looseflints-obsidian-",
            "looseboulders-obsidian-"
    ));

    private final String label;
    private final List<String> requiredTokens;
    private final List<String> acceptedCodePrefixes;

    SurfaceResourcePreset(String label, List<String> requiredTokens) {
        this(label, requiredTokens, List.of());
    }

    SurfaceResourcePreset(
            String label,
            List<String> requiredTokens,
            List<String> acceptedCodePrefixes
    ) {
        this.label = label;
        this.requiredTokens = List.copyOf(requiredTokens);
        this.acceptedCodePrefixes = List.copyOf(acceptedCodePrefixes);
    }

    public String label() {
        return label;
    }

    public List<String> requiredTokens() {
        return requiredTokens;
    }

    public List<String> acceptedCodePrefixes() {
        return acceptedCodePrefixes;
    }
}
