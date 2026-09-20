package cartographer.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateManifestParserTest {
    private final UpdateManifestParser parser = new UpdateManifestParser();

    @Test
    void parsesStableManifest() {
        UpdateManifest manifest = parser.parse(validManifest("1.2.3"));

        assertEquals(1, manifest.schemaVersion());
        assertEquals("stable", manifest.channel());
        assertEquals(
                ApplicationVersion.parse("1.2.3"),
                manifest.version()
        );
        assertEquals(
                "VS-Cartographer-Setup-1.2.3.exe",
                manifest.installerFile()
        );
        assertEquals(123456L, manifest.installerSize());
    }

    @Test
    void rejectsUnsupportedSchemaAndChannel() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        validManifest("1.2.3")
                                .replace("schemaVersion=1", "schemaVersion=2")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        validManifest("1.2.3")
                                .replace("channel=stable", "channel=beta")
                )
        );
    }

    @Test
    void rejectsMissingOrUnsafeFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        validManifest("1.2.3")
                                .replace(
                                        "installerSha256="
                                                + "a".repeat(64),
                                        "installerSha256=bad"
                                )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        validManifest("1.2.3")
                                .replace(
                                        "https://github.com/MariuszKam/cartographer/releases/download/v1.2.3/",
                                        "http://github.com/MariuszKam/cartographer/releases/download/v1.2.3/"
                                )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        validManifest("1.2.3")
                                .replace("installerSize=123456", "installerSize=0")
                )
        );
    }

    static String validManifest(String version) {
        return """
                schemaVersion=1
                channel=stable
                version=%s
                installerFile=VS-Cartographer-Setup-%s.exe
                installerUrl=https://github.com/MariuszKam/cartographer/releases/download/v%s/VS-Cartographer-Setup-%s.exe
                installerSha256=%s
                installerSize=123456
                releaseUrl=https://github.com/MariuszKam/cartographer/releases/tag/v%s
                """.formatted(
                version,
                version,
                version,
                version,
                "a".repeat(64),
                version
        );
    }
}
