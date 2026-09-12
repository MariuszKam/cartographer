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

        return (normalizedCode.startsWith("ore-poor-")
                || normalizedCode.startsWith("ore-medium-")
                || normalizedCode.startsWith("ore-rich-")
                || normalizedCode.contains(":ore-poor-")
                || normalizedCode.contains(":ore-medium-")
                || normalizedCode.contains(":ore-rich-"))
                && normalizedCode.contains(normalizedMatch);
    }
}
