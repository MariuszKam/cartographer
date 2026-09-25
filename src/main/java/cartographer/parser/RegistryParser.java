package cartographer.parser;

import cartographer.model.BlockInfo;
import cartographer.binary.ProtobufWireReader;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class RegistryParser {
    private static final int SAVEGAME_MODDATA_FIELD = 11;
    private static final int MAP_ENTRY_KEY_FIELD = 1;
    private static final int MAP_ENTRY_VALUE_FIELD = 2;
    private static final int BLOCK_IDS_ENTRY_FIELD = 1;
    private static final int BLOCK_ID_FIELD = 1;
    private static final int BLOCK_CODE_FIELD = 2;

    public Map<Integer, BlockInfo> parse(byte[] payload) {
        Map<Integer, BlockInfo> blocks = new HashMap<>();
        if (payload == null || payload.length == 0) {
            return blocks;
        }

        List<byte[]> modDataEntries =
                ProtobufWireReader.readLengthDelimitedFields(
                        payload,
                        SAVEGAME_MODDATA_FIELD
                );

        for (byte[] modDataEntry : modDataEntries) {
            Optional<String> key =
                    readString(
                            modDataEntry,
                            MAP_ENTRY_KEY_FIELD
                    );

            if (key.isEmpty()
                    || !"BlockIDs".equals(key.get())) {
                continue;
            }

            Optional<byte[]> value =
                    ProtobufWireReader.readLengthDelimitedField(
                            modDataEntry,
                            MAP_ENTRY_VALUE_FIELD
                    );

            value.ifPresent(
                    bytes ->
                            blocks.putAll(
                                    parseBlockIds(
                                            bytes
                                    )
                            )
            );
        }

        return blocks;
    }

    private Map<Integer, BlockInfo> parseBlockIds(
            byte[] payload
    ) {
        Map<Integer, BlockInfo> blocks =
                new HashMap<>();

        for (byte[] entry :
                ProtobufWireReader.readLengthDelimitedFields(
                        payload,
                        BLOCK_IDS_ENTRY_FIELD
                )) {

            OptionalLong id =
                    ProtobufWireReader.readVarIntField(
                            entry,
                            BLOCK_ID_FIELD
                    );

            Optional<String> code =
                    readString(
                            entry,
                            BLOCK_CODE_FIELD
                    );

            if (id.isPresent()
                    && code.isPresent()) {
                blocks.put(
                        (int) id.getAsLong(),
                        new BlockInfo(
                                (int) id.getAsLong(),
                                code.get()
                        )
                );
            }
        }

        return blocks;
    }

    private Optional<String> readString(
            byte[] payload,
            int fieldNumber
    ) {
        return ProtobufWireReader.readLengthDelimitedField(
                        payload,
                        fieldNumber
                )
                .map(
                        bytes ->
                                new String(
                                        bytes,
                                        StandardCharsets.UTF_8
                                )
                );
    }
}
