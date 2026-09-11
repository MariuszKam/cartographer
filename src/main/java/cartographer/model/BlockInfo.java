package cartographer.model;

import java.util.Locale;

public record BlockInfo(int id, String code) {
    public static BlockInfo unknown(int id) {
        return new BlockInfo(id, "unknown:" + id);
    }

    public boolean isAir() {
        String normalized = normalizedCode();
        return id == 0 || normalized.equals("air") || normalized.equals("game:air") || normalized.endsWith(":air");
    }

    public boolean isFoliage() {
        String normalized = normalizedCode();
        return normalized.contains("leaves")
                || normalized.contains("foliage")
                || normalized.contains("grass")
                || normalized.contains("flower")
                || normalized.contains("mushroom")
                || normalized.contains("sapling")
                || normalized.contains("crop");
    }

    public String materialType() {
        String normalized = normalizedCode();
        if (isAir()) {
            return "air";
        }
        if (normalized.contains("water")) {
            return "water";
        }
        if (normalized.contains("lava")) {
            return "lava";
        }
        if (normalized.contains("rock") || normalized.contains("stone") || normalized.contains("ore")) {
            return "rock";
        }
        if (normalized.contains("soil") || normalized.contains("sand") || normalized.contains("gravel") || normalized.contains("clay")) {
            return "ground";
        }
        if (normalized.contains("wood") || normalized.contains("log") || normalized.contains("plank")) {
            return "wood";
        }
        if (isFoliage()) {
            return "foliage";
        }
        return "solid";
    }

    private String normalizedCode() {
        return code == null ? "" : code.toLowerCase(Locale.ROOT);
    }
}
