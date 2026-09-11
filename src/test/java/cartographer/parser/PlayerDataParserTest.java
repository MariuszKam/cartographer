package cartographer.parser;

import cartographer.model.ParseResult;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataParserTest {
    @Test
    void parsesFixtureProtobufFixed64Position() {
        ByteBuffer buffer = ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x09).putDouble(512341.4);
        buffer.put((byte) 0x11).putDouble(112.0);
        buffer.put((byte) 0x19).putDouble(511782.7);

        ParseResult<WorldPosition> result = new PlayerDataParser().parse(buffer.array());

        assertTrue(result.isSuccess());
        WorldPosition position = result.value().orElseThrow();
        assertEquals(512341.4, position.x(), 0.0001);
        assertEquals(112.0, position.y(), 0.0001);
        assertEquals(511782.7, position.z(), 0.0001);
    }

    @Test
    void rejectsAllZeroBinaryTripleAsFalsePositive() {
        byte[] payload = ByteBuffer.allocate(24)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(0.0)
                .putDouble(0.0)
                .putDouble(0.0)
                .array();

        ParseResult<WorldPosition> result = new PlayerDataParser().parse(payload);

        assertFalse(result.isSuccess());
    }

    @Test
    void ranksLikelyPositionCandidates() {
        ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(8);
        buffer.putDouble(9.0).putDouble(6.0).putDouble(9.0);
        buffer.position(40);
        buffer.putFloat(512341.5f).putFloat(112.0f).putFloat(511782.75f);

        PlayerPositionCandidate candidate = new PlayerDataParser().findCandidates(buffer.array(), 1).get(0);

        assertEquals("float-le", candidate.encoding());
        assertEquals(512341.5, candidate.position().x(), 0.5);
        assertEquals(112.0, candidate.position().y(), 0.5);
        assertEquals(511782.75, candidate.position().z(), 0.5);
    }
}
