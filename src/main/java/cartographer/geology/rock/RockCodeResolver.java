package cartographer.geology.rock;

import cartographer.model.BlockInfo;

import java.util.Locale;
import java.util.Optional;

public final class RockCodeResolver {

    public Optional<RockIdentity> resolve(BlockInfo blockInfo) {
        if (blockInfo == null || blockInfo.code() == null) {
            return Optional.empty();
        }

        String code = blockInfo.code().trim().toLowerCase(Locale.ROOT);
        if (code.isBlank() || containsWhitespace(code)) {
            return Optional.empty();
        }

        int separator = code.indexOf(':');
        String namespace = "";
        String path = code;
        if (separator >= 0) {
            if (separator == 0 || separator != code.lastIndexOf(':')) {
                return Optional.empty();
            }
            namespace = code.substring(0, separator);
            path = code.substring(separator + 1);
        }

        if (!validNamespace(namespace)
                || !path.startsWith("rock-")) {
            return Optional.empty();
        }

        String rockName = path.substring("rock-".length());
        if (!validAssetPart(rockName)) {
            return Optional.empty();
        }

        return Optional.of(
                new RockIdentity(
                        blockInfo.id(),
                        code,
                        namespace,
                        rockName
                )
        );
    }

    private boolean validNamespace(String namespace) {
        return namespace.isEmpty() || validAssetPart(namespace);
    }

    private boolean validAssetPart(String value) {
        if (value.isEmpty()) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-'
                    || character == '_'
                    || character == '.')) {
                return false;
            }
        }
        return true;
    }

    private boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
