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

        return path.startsWith("ore-")
                && path.contains(normalizedMatch);
    }

    private static String pathPart(String normalizedCode) {
        int namespaceSeparator = normalizedCode.indexOf(':');

        return namespaceSeparator >= 0
                ? normalizedCode.substring(namespaceSeparator + 1)
                : normalizedCode;
    }
}
