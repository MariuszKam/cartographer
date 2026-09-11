package cartographer.save;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;

public final class ProtobufWireReader {

    private ProtobufWireReader() {
    }

    public static Optional<byte[]> readLengthDelimitedField(
            byte[] data,
            int wantedFieldNumber
    ) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }

        Cursor cursor = new Cursor();

        while (cursor.position < data.length) {
            long key = readVarInt(data, cursor);

            int fieldNumber = (int) (key >>> 3);
            int wireType = (int) (key & 0b111);

            switch (wireType) {
                case 0 -> readVarInt(data, cursor);

                case 1 -> skip(
                        data,
                        cursor,
                        8
                );

                case 2 -> {
                    int length = readLength(
                            data,
                            cursor
                    );

                    if (fieldNumber == wantedFieldNumber) {
                        return Optional.of(
                                Arrays.copyOfRange(
                                        data,
                                        cursor.position,
                                        cursor.position + length
                                )
                        );
                    }

                    skip(
                            data,
                            cursor,
                            length
                    );
                }

                case 5 -> skip(
                        data,
                        cursor,
                        4
                );

                default -> throw new IllegalStateException(
                        "Unsupported protobuf wire type "
                                + wireType
                                + " at byte "
                                + cursor.position
                );
            }
        }

        return Optional.empty();
    }

    public static OptionalLong readVarIntField(
            byte[] data,
            int wantedFieldNumber
    ) {
        if (data == null || data.length == 0) {
            return OptionalLong.empty();
        }

        Cursor cursor = new Cursor();

        while (cursor.position < data.length) {
            long key = readVarInt(
                    data,
                    cursor
            );

            int fieldNumber =
                    (int) (key >>> 3);

            int wireType =
                    (int) (key & 0b111);

            if (fieldNumber == wantedFieldNumber) {
                if (wireType != 0) {
                    throw new IllegalStateException(
                            "Expected protobuf varint for field "
                                    + wantedFieldNumber
                                    + " but wire type was "
                                    + wireType
                    );
                }

                return OptionalLong.of(
                        readVarInt(
                                data,
                                cursor
                        )
                );
            }

            skipField(
                    data,
                    cursor,
                    wireType
            );
        }

        return OptionalLong.empty();
    }

    private static void skipField(
            byte[] data,
            Cursor cursor,
            int wireType
    ) {
        switch (wireType) {
            case 0 -> readVarInt(
                    data,
                    cursor
            );

            case 1 -> skip(
                    data,
                    cursor,
                    8
            );

            case 2 -> {
                int length = readLength(
                        data,
                        cursor
                );

                skip(
                        data,
                        cursor,
                        length
                );
            }

            case 5 -> skip(
                    data,
                    cursor,
                    4
            );

            default -> throw new IllegalStateException(
                    "Unsupported protobuf wire type "
                            + wireType
                            + " at byte "
                            + cursor.position
            );
        }
    }

    private static int readLength(
            byte[] data,
            Cursor cursor
    ) {
        long lengthValue =
                readVarInt(
                        data,
                        cursor
                );

        if (lengthValue < 0
                || lengthValue > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Invalid protobuf field length: "
                            + lengthValue
            );
        }

        int length =
                (int) lengthValue;

        if (cursor.position + length > data.length) {
            throw new IllegalStateException(
                    "Length-delimited protobuf field exceeds payload"
            );
        }

        return length;
    }

    private static long readVarInt(
            byte[] data,
            Cursor cursor
    ) {
        long result = 0;
        int shift = 0;

        while (shift < 64) {
            if (cursor.position >= data.length) {
                throw new IllegalStateException(
                        "Unexpected end of protobuf varint"
                );
            }

            int current =
                    Byte.toUnsignedInt(
                            data[cursor.position++]
                    );

            result |=
                    (long) (current & 0x7F)
                            << shift;

            if ((current & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalStateException(
                "Invalid protobuf varint"
        );
    }

    private static void skip(
            byte[] data,
            Cursor cursor,
            int byteCount
    ) {
        if (byteCount < 0
                || cursor.position + byteCount > data.length) {
            throw new IllegalStateException(
                    "Unexpected end of protobuf payload"
            );
        }

        cursor.position += byteCount;
    }

    private static final class Cursor {
        private int position;
    }
}