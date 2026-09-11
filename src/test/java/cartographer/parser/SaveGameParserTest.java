package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveGameParserTest {

    @Test
    void parsesWorldSizeFromSaveGame() {
        byte[] payload =
                createSaveGame(
                        1_024_000,
                        256,
                        1_024_000
                );

        ParseResult<WorldMetadata> result =
                new SaveGameParser()
                        .parse(payload);

        assertTrue(
                result.isSuccess(),
                () -> result
                        .error()
                        .orElse("unknown error")
        );

        WorldMetadata metadata =
                result.value()
                        .orElseThrow();

        assertEquals(
                1_024_000,
                metadata.mapSizeX()
        );

        assertEquals(
                256,
                metadata.mapSizeY()
        );

        assertEquals(
                1_024_000,
                metadata.mapSizeZ()
        );
    }

    private byte[] createSaveGame(
            int x,
            int y,
            int z
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        // field 1, wire type 0
        out.write(0x08);
        writeVarInt(out, x);

        // field 2, wire type 0
        out.write(0x10);
        writeVarInt(out, y);

        // field 3, wire type 0
        out.write(0x18);
        writeVarInt(out, z);

        return out.toByteArray();
    }

    private void writeVarInt(
            ByteArrayOutputStream out,
            int value
    ) {
        int remaining = value;

        while (remaining >= 0x80) {
            out.write(
                    (remaining & 0x7F)
                            | 0x80
            );

            remaining >>>= 7;
        }

        out.write(remaining);
    }
}