package cartographer.parser;

import cartographer.model.IntDataMap2D;
import cartographer.model.ParseResult;
import cartographer.save.ProtobufWireReader;

import java.util.List;
import java.util.OptionalLong;

public class IntDataMap2DParser {
    private static final int DATA_FIELD =
            1;

    private static final int SIZE_FIELD =
            2;

    private static final int TOP_LEFT_PADDING_FIELD =
            3;

    private static final int BOTTOM_RIGHT_PADDING_FIELD =
            4;

    public ParseResult<IntDataMap2D> parse(
            byte[] payload
    ) {
        if (payload == null
                || payload.length == 0) {

            return ParseResult.failure(
                    "IntDataMap2D payload is empty"
            );
        }

        try {
            List<Long> rawValues =
                    ProtobufWireReader.readRepeatedUInt32Field(
                            payload,
                            DATA_FIELD
                    );

            OptionalLong size =
                    ProtobufWireReader.readVarIntField(
                            payload,
                            SIZE_FIELD
                    );

            if (size.isEmpty()) {
                return ParseResult.failure(
                        "IntDataMap2D has no Size field"
                );
            }

            if (rawValues.isEmpty()) {
                return ParseResult.failure(
                        "IntDataMap2D has no Data field"
                );
            }

            int[] values =
                    new int[rawValues.size()];

            for (int index = 0; index < rawValues.size(); index++) {
                long value =
                        rawValues.get(
                                index
                        );

                if (value < Integer.MIN_VALUE
                        || value > 0xFFFF_FFFFL) {

                    return ParseResult.failure(
                            "IntDataMap2D Data value at index "
                                    + index
                                    + " is outside int32 range"
                    );
                }

                values[index] =
                        (int) value;
            }

            int mapSize =
                    checkedInt(
                            "Size",
                            size.getAsLong()
                    );

            int topLeftPadding =
                    checkedInt(
                            "TopLeftPadding",
                            ProtobufWireReader.readVarIntField(
                                            payload,
                                            TOP_LEFT_PADDING_FIELD
                                    )
                                    .orElse(0)
                    );

            int bottomRightPadding =
                    checkedInt(
                            "BottomRightPadding",
                            ProtobufWireReader.readVarIntField(
                                            payload,
                                            BOTTOM_RIGHT_PADDING_FIELD
                                    )
                                    .orElse(0)
                    );

            return ParseResult.success(
                    new IntDataMap2D(
                            mapSize,
                            topLeftPadding,
                            bottomRightPadding,
                            values
                    )
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ParseResult.failure(
                    "invalid IntDataMap2D protobuf: "
                            + exception.getMessage()
            );
        }
    }

    private int checkedInt(
            String field,
            long value
    ) {
        if (value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    field
                            + " is outside int32 range"
            );
        }

        return (int) value;
    }
}
