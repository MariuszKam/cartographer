package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataParserTest {

    @Test
    void parsesVintageStoryEntityPlayerSerializedFromField3() {
        byte[] entityPlayer = createEntityPlayer(
                512341.4,
                112.0,
                511782.7
        );

        byte[] playerData =
                wrapAsEntityPlayerSerializedField(entityPlayer);

        ParseResult<WorldPosition> result =
                new PlayerDataParser().parse(playerData);

        assertTrue(
                result.isSuccess(),
                () -> result.error().orElse("unknown error")
        );

        WorldPosition position =
                result.value().orElseThrow();

        assertEquals(
                512341.4,
                position.x(),
                0.0001
        );

        assertEquals(
                112.0,
                position.y(),
                0.0001
        );

        assertEquals(
                511782.7,
                position.z(),
                0.0001
        );
    }

    @Test
    void rejectsPayloadWithoutEntityPlayerField() {
        byte[] invalidPlayerData = {
                0x0A,
                0x03,
                'a',
                'b',
                'c'
        };

        ParseResult<WorldPosition> result =
                new PlayerDataParser()
                        .parse(invalidPlayerData);

        assertFalse(result.isSuccess());
    }

    private byte[] createEntityPlayer(
            double x,
            double y,
            double z
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeDotNetString(
                out,
                "EntityPlayer"
        );

        writeDotNetString(
                out,
                "1.21.0"
        );

        // EntityId
        writeLongLE(
                out,
                123L
        );

        /*
         * Empty WatchedAttributes.
         * TreeAttribute terminator.
         */
        out.write(0);

        // EntityPos
        writeDoubleLE(out, x);
        writeDoubleLE(out, y);
        writeDoubleLE(out, z);

        /*
         * EntityPos contains more fields after XYZ,
         * but our parser intentionally stops after Z.
         */
        return out.toByteArray();
    }

    private byte[] wrapAsEntityPlayerSerializedField(
            byte[] entityPlayer
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        /*
         * protobuf:
         *
         * field 3
         * wire type 2
         *
         * (3 << 3) | 2 = 26 = 0x1A
         */
        out.write(0x1A);

        writeVarInt(
                out,
                entityPlayer.length
        );

        out.writeBytes(entityPlayer);

        return out.toByteArray();
    }

    private void writeDotNetString(
            ByteArrayOutputStream out,
            String value
    ) {
        byte[] bytes =
                value.getBytes(StandardCharsets.UTF_8);

        writeVarInt(
                out,
                bytes.length
        );

        out.writeBytes(bytes);
    }

    private void writeDoubleLE(
            ByteArrayOutputStream out,
            double value
    ) {
        writeLongLE(
                out,
                Double.doubleToLongBits(value)
        );
    }

    private void writeLongLE(
            ByteArrayOutputStream out,
            long value
    ) {
        for (int index = 0; index < 8; index++) {
            out.write(
                    (int) ((value >>> (index * 8)) & 0xFF)
            );
        }
    }

    private void writeVarInt(
            ByteArrayOutputStream out,
            int value
    ) {
        int remaining = value;

        while (remaining >= 0x80) {
            out.write(
                    (remaining & 0x7F) | 0x80
            );

            remaining >>>= 7;
        }

        out.write(remaining);
    }
}