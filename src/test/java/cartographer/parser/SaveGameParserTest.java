package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveGameParserTest {

    private static final int MAP_SIZE_X =
            1_024_000;

    private static final int MAP_SIZE_Y =
            256;

    private static final int MAP_SIZE_Z =
            1_024_000;

    @Test
    void parsesWorldSizeFromSaveGame() {
        byte[] payload =
                createSaveGame();

        ParseResult<WorldMetadata> result =
                new SaveGameParser()
                        .parse(
                                payload
                        );

        assertTrue(
                result.isSuccess(),
                () ->
                        result.error()
                                .orElse(
                                        "unknown error"
                                )
        );

        WorldMetadata metadata =
                result.value()
                        .orElseThrow();

        assertEquals(
                MAP_SIZE_X,
                metadata.mapSizeX()
        );

        assertEquals(
                MAP_SIZE_Y,
                metadata.mapSizeY()
        );

        assertEquals(
                MAP_SIZE_Z,
                metadata.mapSizeZ()
        );
    }

    private byte[] createSaveGame() {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        // field 1, wire type 0
        out.write(
                0x08
        );

        writeVarInt(
                out,
                MAP_SIZE_X
        );

        // field 2, wire type 0
        out.write(
                0x10
        );

        writeVarInt(
                out,
                MAP_SIZE_Y
        );

        // field 3, wire type 0
        out.write(
                0x18
        );

        writeVarInt(
                out,
                MAP_SIZE_Z
        );

        return out.toByteArray();
    }

    private void writeVarInt(
            ByteArrayOutputStream out,
            int value
    ) {
        int remaining =
                value;

        while (remaining >= 0x80) {
            out.write(
                    (remaining & 0x7F)
                            | 0x80
            );

            remaining >>>=
                    7;
        }

        out.write(
                remaining
        );
    }
}