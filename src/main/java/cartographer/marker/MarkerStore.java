package cartographer.marker;

import cartographer.model.DisplayPosition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public class MarkerStore {

    private final Path baseDirectory;

    public MarkerStore(
            Path baseDirectory
    ) {
        this.baseDirectory = Objects.requireNonNull(
                baseDirectory,
                "baseDirectory is required"
        ).toAbsolutePath().normalize();
    }

    /*
     * Per-save marker API.
     *
     * Coordinates inside UserMarker are display coordinates.
     */
    public List<UserMarker> load(
            Path savePath
    ) {
        Path path =
                perSavePath(
                        savePath
                );

        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            List<UserMarker> markers =
                    new ArrayList<>();

            for (String line :
                    Files.readAllLines(
                            path,
                            StandardCharsets.UTF_8
                    )) {

                if (line.isBlank()) {
                    continue;
                }

                String[] parts =
                        line.split(
                                "\t",
                                3
                        );

                if (parts.length != 3) {
                    throw new IllegalStateException(
                            "Malformed marker entry in "
                                    + path
                    );
                }

                double x =
                        Double.parseDouble(
                                parts[0]
                        );

                double z =
                        Double.parseDouble(
                                parts[1]
                        );

                String name =
                        unescapeName(
                                parts[2]
                        );

                markers.add(
                        new UserMarker(
                                name,
                                new DisplayPosition(x, 0.0, z)
                        )
                );
            }

            markers.sort(
                    Comparator.comparing(
                            UserMarker::name,
                            String.CASE_INSENSITIVE_ORDER
                    )
            );

            return List.copyOf(
                    markers
            );

        } catch (IOException
                 | NumberFormatException exception) {

            throw new IllegalStateException(
                    "Cannot read markers for save "
                            + savePath
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /*
     * Add-or-replace.
     *
     * Marker names are unique per save, case-insensitively.
     */
    public void put(
            Path savePath,
            UserMarker marker
    ) {
        List<UserMarker> markers =
                new ArrayList<>(
                        load(
                                savePath
                        )
                );

        replaceByName(
                markers,
                marker
        );

        writePerSave(
                savePath,
                markers
        );
    }

    public boolean remove(
            Path savePath,
            String name
    ) {
        List<UserMarker> markers =
                new ArrayList<>(
                        load(
                                savePath
                        )
                );

        boolean removed =
                markers.removeIf(
                        marker ->
                                marker.name()
                                        .equalsIgnoreCase(
                                                name
                                        )
                );

        if (!removed) {
            return false;
        }

        writePerSave(
                savePath,
                markers
        );

        return true;
    }

    public int clear(
            Path savePath
    ) {
        List<UserMarker> current =
                load(
                        savePath
                );

        if (current.isEmpty()) {
            return 0;
        }

        Path path =
                perSavePath(
                        savePath
                );

        try {
            Files.deleteIfExists(
                    path
            );

            return current.size();

        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Cannot clear markers for save "
                            + savePath
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void replaceByName(
            List<UserMarker> markers,
            UserMarker marker
    ) {
        markers.removeIf(
                existing ->
                        existing.name()
                                .equalsIgnoreCase(
                                        marker.name()
                                )
        );

        markers.add(
                marker
        );

        markers.sort(
                Comparator.comparing(
                        UserMarker::name,
                        String.CASE_INSENSITIVE_ORDER
                )
        );
    }

    private void writePerSave(
            Path savePath,
            List<UserMarker> markers
    ) {
        Path path =
                perSavePath(
                        savePath
                );

        writeTabbed(
                path,
                markers
        );
    }

    private void writeTabbed(
            Path path,
            List<UserMarker> markers
    ) {
        try {
            Path parent =
                    path.getParent();

            if (parent != null) {
                Files.createDirectories(
                        parent
                );
            }

            StringBuilder content =
                    new StringBuilder();

            for (UserMarker marker : markers) {
                content.append(
                                marker.position().x()
                        )
                        .append('\t')
                        .append(
                                marker.position().z()
                        )
                        .append('\t')
                        .append(
                                escapeName(
                                        marker.name()
                                )
                        )
                        .append(
                                System.lineSeparator()
                        );
            }

            Files.writeString(
                    path,
                    content.toString(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Cannot write markers at "
                            + path
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private Path perSavePath(
            Path savePath
    ) {
        Path normalized =
                savePath
                        .toAbsolutePath()
                        .normalize();

        String fileName =
                normalized.getFileName() == null
                        ? "save"
                        : normalized
                        .getFileName()
                        .toString();

        String sanitized =
                fileName.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        String hash =
                shortHash(
                        normalized.toString()
                );

        return baseDirectory
                .resolve(
                        "markers"
                )
                .resolve(
                        sanitized
                                + "-"
                                + hash
                                + ".markers"
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

            return HexFormat.of()
                    .formatHex(
                            bytes
                    )
                    .substring(
                            0,
                            12
                    );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    /*
     * New per-save format is tab-separated.
     * Escape control characters so marker labels remain one line.
     */
    private String escapeName(
            String value
    ) {
        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\t",
                        "\\t"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }

    private String unescapeName(
            String value
    ) {
        StringBuilder output =
                new StringBuilder();

        boolean escaped =
                false;

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(
                            index
                    );

            if (!escaped) {
                if (character == '\\') {
                    escaped =
                            true;

                } else {
                    output.append(
                            character
                    );
                }

                continue;
            }

            switch (character) {
                case 't' ->
                        output.append(
                                '\t'
                        );

                case 'r' ->
                        output.append(
                                '\r'
                        );

                case 'n' ->
                        output.append(
                                '\n'
                        );

                case '\\' ->
                        output.append(
                                '\\'
                        );

                default -> {
                    output.append(
                            '\\'
                    );

                    output.append(
                            character
                    );
                }
            }

            escaped =
                    false;
        }

        if (escaped) {
            output.append(
                    '\\'
            );
        }

        return output.toString();
    }


}