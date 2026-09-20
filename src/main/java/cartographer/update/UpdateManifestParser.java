package cartographer.update;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Properties;

public final class UpdateManifestParser {

    public UpdateManifest parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Update manifest is empty");
        }

        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Cannot parse update manifest",
                    exception
            );
        }

        int schemaVersion = parseInteger(
                required(properties, "schemaVersion"),
                "schemaVersion"
        );
        long installerSize = parseLong(
                required(properties, "installerSize"),
                "installerSize"
        );

        return new UpdateManifest(
                schemaVersion,
                required(properties, "channel"),
                ApplicationVersion.parse(required(properties, "version")),
                required(properties, "installerFile"),
                parseUri(required(properties, "installerUrl"), "installerUrl"),
                required(properties, "installerSha256"),
                installerSize,
                parseUri(required(properties, "releaseUrl"), "releaseUrl")
        );
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Update manifest is missing " + key
            );
        }
        return value.trim();
    }

    private int parseInteger(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be an integer",
                    exception
            );
        }
    }

    private long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be an integer",
                    exception
            );
        }
    }

    private URI parseUri(String value, String field) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    field + " is not a valid URI",
                    exception
            );
        }
    }
}
