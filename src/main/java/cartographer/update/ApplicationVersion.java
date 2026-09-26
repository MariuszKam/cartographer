package cartographer.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Stable semantic version used by the application and release/update pipeline.
 *
 * <p>Auto Update v1 intentionally supports only MAJOR.MINOR.PATCH releases.
 */
public record ApplicationVersion(int major, int minor, int patch)
        implements Comparable<ApplicationVersion> {

    private static final Pattern STABLE_SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$"
    );
    private static final String BUILD_INFO_RESOURCE =
            "/cartographer-build.properties";

    public ApplicationVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "Version components cannot be negative"
            );
        }
    }

    public static ApplicationVersion current() {
        Properties properties = new Properties();
        try (InputStream input =
                     ApplicationVersion.class.getResourceAsStream(
                             BUILD_INFO_RESOURCE
                     )) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing runtime build metadata: "
                                + BUILD_INFO_RESOURCE
                );
            }
            properties.load(new InputStreamReader(
                    input,
                    StandardCharsets.UTF_8
            ));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read runtime build metadata",
                    exception
            );
        }

        String rawVersion = properties.getProperty("version");
        if (rawVersion == null || rawVersion.isBlank()) {
            throw new IllegalStateException(
                    "Runtime build metadata does not contain version"
            );
        }

        try {
            return parse(rawVersion.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Runtime build metadata contains invalid version: "
                            + rawVersion,
                    exception
            );
        }
    }

    public static ApplicationVersion parse(String rawVersion) {
        Objects.requireNonNull(rawVersion, "rawVersion is required");
        Matcher matcher = STABLE_SEMVER.matcher(rawVersion);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Version must use stable MAJOR.MINOR.PATCH form: "
                            + rawVersion
            );
        }

        try {
            return new ApplicationVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Version component exceeds supported integer range: "
                            + rawVersion,
                    exception
            );
        }
    }

    @Override
    public int compareTo(@NotNull ApplicationVersion other) {
        Objects.requireNonNull(other, "other is required");

        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }

        int minorComparison = Integer.compare(minor, other.minor);
        if (minorComparison != 0) {
            return minorComparison;
        }

        return Integer.compare(patch, other.patch);
    }

    @Override
    public @NotNull String toString() {
        return major + "." + minor + "." + patch;
    }
}
