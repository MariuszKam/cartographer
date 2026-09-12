package cartographer.parser;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryParserTest {

    @Test
    void parsesSaveGameModDataBlockIdsRegistry() {
        byte[] saveGame =
                saveGameWithBlockIds(
                        blockIds(
                                blockId(
                                        0,
                                        "air"
                                ),
                                blockId(
                                        1,
                                        "mantle"
                                ),
                                blockId(
                                        27,
                                        "soil-medium-none"
                                )
                        )
                );

        Map<Integer, BlockInfo> blocks =
                new RegistryParser()
                        .parse(
                                saveGame
                        );

        assertEquals(
                "air",
                blocks.get(0)
                        .code()
        );

        assertEquals(
                "mantle",
                blocks.get(1)
                        .code()
        );

        assertEquals(
                "soil-medium-none",
                blocks.get(27)
                        .code()
        );
    }

    @Test
    void ignoresOtherSaveGameModDataEntries() {
        ByteArrayOutputStream saveGame =
                new ByteArrayOutputStream();

        writeLengthDelimited(
                saveGame,
                11,
                mapEntry(
                        "ItemIDs",
                        blockIds(
                                blockId(
                                        1,
                                        "not-a-block"
                                )
                        )
                )
        );

        assertTrue(
                new RegistryParser()
                        .parse(
                                saveGame.toByteArray()
                        )
                        .isEmpty()
        );
    }

    private byte[] saveGameWithBlockIds(
            byte[] blockIds
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeLengthDelimited(
                out,
                11,
                mapEntry(
                        "BlockIDs",
                        blockIds
                )
        );

        return out.toByteArray();
    }

    private byte[] mapEntry(
            String key,
            byte[] value
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeString(
                out,
                1,
                key
        );

        writeLengthDelimited(
                out,
                2,
                value
        );

        return out.toByteArray();
    }

    private byte[] blockIds(
            byte[]... entries
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        for (byte[] entry : entries) {
            writeLengthDelimited(
                    out,
                    1,
                    entry
            );
        }

        return out.toByteArray();
    }

    private byte[] blockId(
            int id,
            String code
    ) {
        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        writeBlockIdValue(
                out,
                id
        );

        writeString(
                out,
                2,
                code
        );

        return out.toByteArray();
    }

    private void writeString(
            ByteArrayOutputStream out,
            int fieldNumber,
            String value
    ) {
        writeLengthDelimited(
                out,
                fieldNumber,
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private void writeLengthDelimited(
            ByteArrayOutputStream out,
            int fieldNumber,
            byte[] value
    ) {
        writeVarInt(
                out,
                (fieldNumber << 3)
                        | 2
        );

        writeVarInt(
                out,
                value.length
        );

        out.writeBytes(
                value
        );
    }

    private void writeBlockIdValue(
            ByteArrayOutputStream out,
            int value
    ) {
        writeVarInt(
                out,
                1 << 3
        );

        writeVarInt(
                out,
                value
        );
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