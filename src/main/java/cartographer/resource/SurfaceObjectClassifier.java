package cartographer.resource;

import cartographer.model.BlockInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class SurfaceObjectClassifier {
    private static final Set<String> VISUAL_VARIANTS = Set.of(
            "free",
            "snow",
            "water",
            "ice"
    );

    public Optional<SurfaceObjectIdentity> classify(BlockInfo block) {
        return block == null ? Optional.empty() : classify(block.code());
    }

    public Optional<SurfaceObjectIdentity> classify(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
        String path = pathOf(normalizedCode);
        if (path == null) {
            return Optional.empty();
        }

        SurfaceObjectFamily family = familyOf(path);
        if (family == null) {
            return Optional.empty();
        }

        String prefix = prefixOf(family);
        String[] tokens = path.substring(prefix.length()).split("-", -1);
        if (tokens.length < 2 || Arrays.stream(tokens).anyMatch(String::isBlank)) {
            return Optional.empty();
        }

        String variant = tokens[tokens.length - 1];
        if (!VISUAL_VARIANTS.contains(variant)) {
            return Optional.empty();
        }

        List<String> resourceTokens = Arrays.stream(tokens, 0, tokens.length - 1)
                .toList();
        Optional<String> hostRock = Optional.empty();
        if (family == SurfaceObjectFamily.ORE_BITS) {
            if (resourceTokens.size() < 2) {
                return Optional.empty();
            }
            hostRock = Optional.of(resourceTokens.getLast());
            resourceTokens = resourceTokens.subList(0, resourceTokens.size() - 1);
        }

        if (resourceTokens.isEmpty()) {
            return Optional.empty();
        }

        String resourceKey = String.join("-", resourceTokens);
        return Optional.of(new SurfaceObjectIdentity(
                code,
                path,
                family,
                resourceKey,
                displayName(resourceKey),
                hostRock,
                Optional.of(variant)
        ));
    }

    private String pathOf(String normalizedCode) {
        int namespaceSeparator = normalizedCode.indexOf(':');
        if (namespaceSeparator < 0) {
            return normalizedCode;
        }
        if (namespaceSeparator == 0
                || namespaceSeparator == normalizedCode.length() - 1
                || normalizedCode.indexOf(':', namespaceSeparator + 1) >= 0) {
            return null;
        }
        return normalizedCode.substring(namespaceSeparator + 1);
    }

    private SurfaceObjectFamily familyOf(String path) {
        if (path.startsWith("looseores-")) {
            return SurfaceObjectFamily.ORE_BITS;
        }
        if (path.startsWith("looseflints-")) {
            return SurfaceObjectFamily.FLINT;
        }
        if (path.startsWith("loosestones-")) {
            return SurfaceObjectFamily.LOOSE_STONE;
        }
        if (path.startsWith("looseboulders-")) {
            return SurfaceObjectFamily.LOOSE_BOULDER;
        }
        return null;
    }

    private String prefixOf(SurfaceObjectFamily family) {
        return switch (family) {
            case ORE_BITS -> "looseores-";
            case FLINT -> "looseflints-";
            case LOOSE_STONE -> "loosestones-";
            case LOOSE_BOULDER -> "looseboulders-";
        };
    }

    private String displayName(String resourceKey) {
        String words = resourceKey.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
