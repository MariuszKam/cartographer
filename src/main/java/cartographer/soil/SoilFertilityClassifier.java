package cartographer.soil;

import cartographer.model.BlockInfo;

import java.util.Locale;
import java.util.Optional;

public final class SoilFertilityClassifier {
    public Optional<SoilFertilityClassification> classify(BlockInfo block) {
        return block == null ? Optional.empty() : classify(block.code());
    }

    public Optional<SoilFertilityClassification> classify(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        String normalizedPath = pathOf(normalizedCode);
        if (normalizedPath == null) {
            return Optional.empty();
        }

        SoilFertilityClassification classification = classifyPath(
                code,
                normalizedPath
        );
        return Optional.ofNullable(classification);
    }

    private SoilFertilityClassification classifyPath(
            String originalCode,
            String normalizedPath
    ) {
        if (normalizedPath.equals("bonysoil")) {
            return classification(
                    originalCode,
                    normalizedPath,
                    SoilFertilityTier.BONY,
                    SoilFertilitySource.BONY_SOIL
            );
        }

        if (normalizedPath.startsWith("bonysoil-")) {
            String suffix = normalizedPath.substring("bonysoil-".length());
            if (isNumberBetween(suffix, 1, 7)) {
                return classification(
                        originalCode,
                        normalizedPath,
                        SoilFertilityTier.BONY,
                        SoilFertilitySource.BONY_SOIL
                );
            }
            return null;
        }

        if (normalizedPath.startsWith("forestfloor-")) {
            String suffix = normalizedPath.substring("forestfloor-".length());
            if (isNumberBetween(suffix, 0, 7)) {
                return classification(
                        originalCode,
                        normalizedPath,
                        SoilFertilityTier.LOW,
                        SoilFertilitySource.FOREST_FLOOR
                );
            }
            return null;
        }

        String[] tokens = normalizedPath.split("-", -1);
        if (tokens.length == 3 && tokens[0].equals("soil")) {
            SoilFertilityTier tier = tierOf(tokens[1]);
            if (tier != null && isSurfaceVariant(tokens[2])) {
                return classification(
                        originalCode,
                        normalizedPath,
                        tier,
                        SoilFertilitySource.SOIL
                );
            }
            return null;
        }

        if (tokens.length == 3 && tokens[0].equals("farmland")) {
            SoilFertilityTier tier = tierOf(tokens[2]);
            if (tier != null && isMoistureState(tokens[1])) {
                return classification(
                        originalCode,
                        normalizedPath,
                        tier,
                        SoilFertilitySource.FARMLAND
                );
            }
        }

        return null;
    }

    private SoilFertilityClassification classification(
            String originalCode,
            String normalizedPath,
            SoilFertilityTier tier,
            SoilFertilitySource source
    ) {
        return new SoilFertilityClassification(
                originalCode,
                normalizedPath,
                tier,
                source
        );
    }

    private String pathOf(String normalizedCode) {
        int separator = normalizedCode.indexOf(':');
        if (separator < 0) {
            return normalizedCode;
        }
        if (separator == 0
                || separator == normalizedCode.length() - 1
                || normalizedCode.indexOf(':', separator + 1) >= 0) {
            return null;
        }
        if (!normalizedCode.substring(0, separator).equals("game")) {
            return null;
        }
        return normalizedCode.substring(separator + 1);
    }

    private boolean isNumberBetween(String value, int minimum, int maximum) {
        if (value.length() != 1 || value.charAt(0) < '0' || value.charAt(0) > '9') {
            return false;
        }
        int number = value.charAt(0) - '0';
        return number >= minimum && number <= maximum;
    }

    private boolean isSurfaceVariant(String value) {
        return value.equals("none")
                || value.equals("verysparse")
                || value.equals("sparse")
                || value.equals("normal");
    }

    private boolean isMoistureState(String value) {
        return value.equals("dry") || value.equals("moist");
    }

    private SoilFertilityTier tierOf(String value) {
        return switch (value) {
            case "verylow" -> SoilFertilityTier.BARREN;
            case "low" -> SoilFertilityTier.LOW;
            case "medium" -> SoilFertilityTier.MEDIUM;
            case "high" -> SoilFertilityTier.HIGH;
            case "compost" -> SoilFertilityTier.TERRA_PRETA;
            default -> null;
        };
    }
}
