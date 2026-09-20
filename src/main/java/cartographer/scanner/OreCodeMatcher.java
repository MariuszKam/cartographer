package cartographer.scanner;

import java.util.Locale;

public final class OreCodeMatcher {

    private OreCodeMatcher() {
    }

    public static boolean matchesOreCode(
            String code,
            String match
    ) {
        if (code == null || match == null || match.isBlank()) {
            return false;
        }

        String normalizedCode = code.toLowerCase(Locale.ROOT);
        String normalizedMatch = match.trim().toLowerCase(Locale.ROOT);

        String path = pathPart(normalizedCode);

        return isOrePath(path)
                && path.contains(normalizedMatch);
    }

    public static boolean isOreCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return isOrePath(
                pathPart(code.toLowerCase(Locale.ROOT))
        );
    }

    private static boolean isOrePath(String path) {
        return path.startsWith("ore-");
    }

    private static String pathPart(String normalizedCode) {
        int namespaceSeparator = normalizedCode.indexOf(':');

        return namespaceSeparator >= 0
                ? normalizedCode.substring(namespaceSeparator + 1)
                : normalizedCode;
    }
}
