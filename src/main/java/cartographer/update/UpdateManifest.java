package cartographer.update;

import java.net.URI;
import java.util.Objects;

public record UpdateManifest(
        int schemaVersion,
        String channel,
        ApplicationVersion version,
        String installerFile,
        URI installerUri,
        String installerSha256,
        long installerSize,
        URI releaseUri
) {

    public UpdateManifest {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported update manifest schema: " + schemaVersion
            );
        }
        channel = Objects.requireNonNull(channel, "channel is required");
        if (!"stable".equals(channel)) {
            throw new IllegalArgumentException(
                    "Unsupported update channel: " + channel
            );
        }
        Objects.requireNonNull(version, "version is required");
        installerFile = requireInstallerFile(installerFile);
        requireHttpsGithubUri(installerUri, "installerUrl");
        if (!installerUri.getPath().endsWith("/" + installerFile)) {
            throw new IllegalArgumentException(
                    "installerUrl must end with installerFile"
            );
        }
        installerSha256 = requireSha256(installerSha256);
        if (installerSize <= 0L) {
            throw new IllegalArgumentException(
                    "installerSize must be greater than zero"
            );
        }
        requireHttpsGithubUri(releaseUri, "releaseUrl");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return trimmed;
    }

    private static String requireInstallerFile(String value) {
        String fileName = requireText(value, "installerFile");
        if (fileName.contains("/")
                || fileName.contains("\\")
                || !fileName.endsWith(".exe")) {
            throw new IllegalArgumentException(
                    "installerFile must be a Windows EXE base name"
            );
        }
        return fileName;
    }

    private static URI requireHttpsGithubUri(URI uri, String field) {
        Objects.requireNonNull(uri, field + " is required");
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException(
                    field + " must use https://github.com"
            );
        }
        return uri;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "installerSha256")
                .toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "installerSha256 must contain 64 hexadecimal characters"
            );
        }
        return normalized;
    }
}
