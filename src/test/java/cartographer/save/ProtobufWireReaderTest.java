package cartographer.save;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufWireReaderTest {

    @Test
    void lengthDelimitedRangePointsIntoOriginalPayload() {
        byte[] field = new byte[]{10, 20, 30, 40};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, (3 << 3) | 2);
        writeVarInt(out, field.length);
        int expectedOffset = out.size();
        out.writeBytes(field);
        byte[] payload = out.toByteArray();

        ProtobufWireReader.LengthDelimitedFieldRange range =
                ProtobufWireReader.findLengthDelimitedFieldRange(
                                payload,
                                3
                        )
                        .orElseThrow();

        assertEquals(expectedOffset, range.offset());
        assertEquals(field.length, range.length());
        assertEquals(
                expectedOffset + field.length,
                range.endExclusive()
        );
        assertArrayEquals(field, range.copyFrom(payload));

        payload[range.offset()] = 99;
        assertEquals(99, payload[range.offset()]);
        assertEquals(99, range.copyFrom(payload)[0]);
    }

    @Test
    void missingLengthDelimitedFieldReturnsEmpty() {
        byte[] payload = new byte[]{8, 1};

        assertTrue(
                ProtobufWireReader.findLengthDelimitedFieldRange(
                        payload,
                        7
                ).isEmpty()
        );
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
