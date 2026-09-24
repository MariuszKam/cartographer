package cartographer.parser;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapChunkParserTest {
    @Test
    void parsesRealServerMapChunkRainHeightMapField() {
        byte[] payload =
                serverMapChunkWithRepeatedUInt32Field(
                        3,
                        heights(80)
                );

        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        5,
                                        7
                                ),
                                payload
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        MapChunk chunk =
                result.value()
                        .orElseThrow();

        assertTrue(
                chunk.hasRainHeightMap()
        );

        assertFalse(
                chunk.hasWorldGenTerrainHeightMap()
        );

        assertEquals(
                80,
                chunk.heightAt(
                        0,
                        0
                )
        );

        assertEquals(
                111,
                chunk.heightAt(
                        31,
                        0
                )
        );

        assertEquals(
                1103,
                chunk.heightAt(
                        31,
                        31
                )
        );
    }

    @Test
    void parsesRealServerMapChunkWorldGenTerrainHeightMapField() {
        byte[] payload =
                serverMapChunkWithRepeatedUInt32Field(
                        7,
                        heights(120)
                );

        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        -2,
                                        3
                                ),
                                payload
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        MapChunk chunk =
                result.value()
                        .orElseThrow();

        assertFalse(
                chunk.hasRainHeightMap()
        );

        assertTrue(
                chunk.hasWorldGenTerrainHeightMap()
        );

        assertEquals(
                120,
                chunk.heightAt(
                        0,
                        0
                )
        );

        assertEquals(
                1143,
                chunk.heightAt(
                        31,
                        31
                )
        );
    }

    @Test
    void renderedTerrainHeightPrefersRainHeightMapWhenBothHeightFieldsExist() {
        ByteArrayOutputStream payload =
                new ByteArrayOutputStream();

        payload.writeBytes(
                serverMapChunkWithRepeatedUInt32Field(
                        7,
                        heights(500)
                )
        );

        payload.writeBytes(
                serverMapChunkWithRepeatedUInt32Field(
                        3,
                        heights(80)
                )
        );

        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        0,
                                        0
                                ),
                                payload.toByteArray()
                        );

        assertTrue(
                result.isSuccess(),
                () -> result.error()
                        .orElse("unknown error")
        );

        MapChunk chunk =
                result.value()
                        .orElseThrow();

        assertTrue(
                chunk.hasRainHeightMap()
        );

        assertTrue(
                chunk.hasWorldGenTerrainHeightMap()
        );

        assertEquals(
                80,
                chunk.heightAt(
                        0,
                        0
                )
        );

        assertEquals(
                1103,
                chunk.heightAt(
                        31,
                        31
                )
        );
    }

    @Test
    void failsWhenHeightFieldIsMissing() {
        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        0,
                                        0
                                ),
                                new byte[]{0x20, 0x01}
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("no RainHeightMap or WorldGenTerrainHeightMap")
        );
    }

    @Test
    void failsWhenHeightMapLengthIsWrong() {
        byte[] payload =
                serverMapChunkWithRepeatedUInt32Field(
                        3,
                        new int[]{1, 2, 3}
                );

        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        0,
                                        0
                                ),
                                payload
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("1024")
        );
    }

    @Test
    void failsCleanlyOnTruncatedVarint() {
        ParseResult<MapChunk> result =
                new MapChunkParser()
                        .parse(
                                new MapChunkCoordinate(
                                        0,
                                        0
                                ),
                                new byte[]{0x18, (byte) 0x80}
                        );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.error()
                        .orElse("")
                        .contains("invalid ServerMapChunk protobuf")
        );
    }

    private byte[] serverMapChunkWithRepeatedUInt32Field(
            int fieldNumber,
            int[] values
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        int key =
                fieldNumber << 3;

        for (int value : values) {
            writeVarInt(
                    out,
                    key
            );

            writeVarInt(
                    out,
                    value
            );
        }

        return out.toByteArray();
    }

    private int[] heights(
            int start
    ) {
        int[] values =
                new int[32 * 32];

        for (int index = 0; index < values.length; index++) {
            values[index] =
                    start
                            + index;
        }

        return values;
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

            remaining >>>= 7;
        }

        out.write(
                remaining
        );
    }
}
