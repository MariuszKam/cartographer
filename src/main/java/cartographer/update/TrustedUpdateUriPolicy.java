package cartographer.update;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

final class TrustedUpdateUriPolicy {
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_USERCONTENT_SUFFIX =
            ".githubusercontent.com";

    private TrustedUpdateUriPolicy() {
    }

    static boolean isTrustedGithubHttpsUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return GITHUB_HOST.equals(normalizedHost)
                || normalizedHost.endsWith(GITHUB_USERCONTENT_SUFFIX);
    }

    static void requireTrustedGithubHttpsUri(
            URI uri,
            String failureMessage
    ) throws IOException {
        if (!isTrustedGithubHttpsUri(uri)) {
            throw new IOException(failureMessage);
        }
    }
}
