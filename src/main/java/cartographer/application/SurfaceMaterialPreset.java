package cartographer.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The supported surface-material vocabulary used by the application and UI. */
public enum SurfaceMaterialPreset {
    FIRE_CLAY("Fire Clay", List.of("fire", "clay"), List.of("fireclay", "fire clay")),
    CLAY("Clay", List.of("clay"), List.of("clay")),
    PEAT("Peat", List.of("peat"), List.of("peat"));

    private final String label;
    private final List<String> requiredTokens;
    private final List<String> aliases;

    SurfaceMaterialPreset(String label, List<String> requiredTokens, List<String> aliases) {
        this.label = label;
        this.requiredTokens = List.copyOf(requiredTokens);
        this.aliases = aliases.stream()
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .toList();
    }

    public String label() { return label; }
    public List<String> requiredTokens() { return requiredTokens; }

    public static Optional<SurfaceMaterialPreset> resolve(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(preset -> preset.aliases.contains(normalized))
                .findFirst();
    }
}
