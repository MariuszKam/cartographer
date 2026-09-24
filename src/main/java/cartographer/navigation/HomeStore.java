package cartographer.navigation;

import cartographer.model.HomeLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

public class HomeStore {

    private final Path legacyConfigPath;

    public HomeStore(
            Path legacyConfigPath
    ) {
        this.legacyConfigPath =
                legacyConfigPath;
    }

    public Optional<HomeLocation> load(
            Path savePath
    ) {
        return loadFrom(
                perSaveConfigPath(savePath)
        );
    }

    public void save(
            Path savePath,
            HomeLocation home
    ) {
        saveTo(
                perSaveConfigPath(savePath),
                home,
                savePath
        );
    }

    private Optional<HomeLocation> loadFrom(
            Path configPath
    ) {
        if (!Files.exists(configPath)) {
            return Optional.empty();
        }

        Properties properties =
                new Properties();

        try (InputStream input =
                     Files.newInputStream(configPath)) {

            properties.load(input);

            return Optional.of(
                    new HomeLocation(
                            Double.parseDouble(
                                    required(
                                            properties,
                                            "x"
                                    )
                            ),
                            Double.parseDouble(
                                    required(
                                            properties,
                                            "z"
                                    )
                            )
                    )
            );

        } catch (IOException
                 | NumberFormatException exception) {

            throw new IllegalStateException(
                    "Cannot read HOME config at "
                            + configPath
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void saveTo(
            Path configPath,
            HomeLocation home,
            Path savePath
    ) {
        Properties properties =
                new Properties();

        properties.setProperty(
                "x",
                Double.toString(home.x())
        );

        properties.setProperty(
                "z",
                Double.toString(home.z())
        );

        if (savePath != null) {
            properties.setProperty(
                    "save",
                    savePath
                            .toAbsolutePath()
                            .normalize()
                            .toString()
            );
        }

        try {
            Path parent =
                    configPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (OutputStream output =
                         Files.newOutputStream(configPath)) {

                properties.store(
                        output,
                        "VS Cartographer HOME"
                );
            }

        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Cannot write HOME config at "
                            + configPath
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private Path perSaveConfigPath(
            Path savePath
    ) {
        Path normalized =
                savePath
                        .toAbsolutePath()
                        .normalize();

        Path baseDirectory =
                Optional.ofNullable(
                                legacyConfigPath
                                        .toAbsolutePath()
                                        .normalize()
                                        .getParent()
                        )
                        .orElse(
                                Path.of(".")
                                        .toAbsolutePath()
                                        .normalize()
                        );

        String fileName =
                normalized.getFileName() == null
                        ? "save"
                        : normalized
                        .getFileName()
                        .toString();

        String sanitizedName =
                fileName.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        String hash =
                shortHash(
                        normalized.toString()
                );

        return baseDirectory
                .resolve("homes")
                .resolve(
                        sanitizedName
                                + "-"
                                + hash
                                + ".properties"
                );
    }

    private String shortHash(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] bytes =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(bytes)
                    .substring(0, 12);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private String required(
            Properties properties,
            String key
    ) {
        String value =
                properties.getProperty(key);

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "HOME config is missing key: "
                            + key
            );
        }

        return value;
    }
}