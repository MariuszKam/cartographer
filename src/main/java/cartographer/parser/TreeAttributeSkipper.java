package cartographer.parser;

import cartographer.binary.DotNetBinaryReader;

public final class TreeAttributeSkipper {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_ARRAY_LENGTH = 10_000_000;

    private TreeAttributeSkipper() {
    }

    public static void skip(DotNetBinaryReader reader) {
        skip(reader, 0);
    }

    private static void skip(
            DotNetBinaryReader reader,
            int depth
    ) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException(
                    "TreeAttribute nesting exceeds " + MAX_DEPTH
            );
        }

        while (true) {
            int attributeId = reader.readUnsignedByte();

            if (attributeId == 0) {
                return;
            }

            // Every TreeAttribute entry has:
            // byte attributeId
            // .NET string key
            // serialized value
            reader.readDotNetString();

            skipValue(
                    reader,
                    attributeId,
                    depth
            );
        }
    }

    private static void skipValue(
            DotNetBinaryReader reader,
            int attributeId,
            int depth
    ) {
        switch (attributeId) {
            // IntAttribute
            case 1 -> reader.skip(4);

            // LongAttribute
            case 2 -> reader.skip(8);

            // DoubleAttribute
            case 3 -> reader.skip(8);

            // FloatAttribute
            case 4 -> reader.skip(4);

            // StringAttribute
            case 5 -> reader.readDotNetString();

            // TreeAttribute
            case 6 -> skip(reader, depth + 1);

            // ItemstackAttribute
            case 7 -> skipItemStack(reader, depth);

            // ByteArrayAttribute
            case 8 -> {
                int length = reader.readUnsignedShortLE();
                reader.skip(length);
            }

            // BoolAttribute
            case 9 -> reader.skip(1);

            // StringArrayAttribute
            case 10 -> {
                int count = readArrayCount(reader);

                for (int index = 0; index < count; index++) {
                    reader.readDotNetString();
                }
            }

            // IntArrayAttribute
            case 11 -> skipFixedArray(
                    reader,
                    readArrayCount(reader),
                    Integer.BYTES
            );

            // FloatArrayAttribute
            case 12 -> skipFixedArray(
                    reader,
                    readArrayCount(reader),
                    Float.BYTES
            );

            // DoubleArrayAttribute
            case 13 -> skipFixedArray(
                    reader,
                    readArrayCount(reader),
                    Double.BYTES
            );

            // TreeArrayAttribute
            case 14 -> {
                int count = readArrayCount(reader);

                for (int index = 0; index < count; index++) {
                    skip(reader, depth + 1);
                }
            }

            // LongArrayAttribute
            case 15 -> skipFixedArray(
                    reader,
                    readArrayCount(reader),
                    Long.BYTES
            );

            // BoolArrayAttribute
            case 16 -> skipFixedArray(
                    reader,
                    readArrayCount(reader),
                    1
            );

            default -> throw new IllegalStateException(
                    "Unsupported TreeAttribute type "
                            + attributeId
                            + " at byte "
                            + reader.position()
            );
        }
    }

    private static void skipItemStack(
            DotNetBinaryReader reader,
            int depth
    ) {
        boolean isNull = reader.readBoolean();

        if (isNull) {
            return;
        }

        /*
         * Vintage Story ItemStack:
         *
         * int Class
         * int ID
         * int StackSize
         * TreeAttribute Attributes
         */
        reader.skip(
                Integer.BYTES
                        + Integer.BYTES
                        + Integer.BYTES
        );

        skip(reader, depth + 1);
    }

    private static int readArrayCount(
            DotNetBinaryReader reader
    ) {
        int count = reader.readInt32LE();

        if (count < 0 || count > MAX_ARRAY_LENGTH) {
            throw new IllegalStateException(
                    "Invalid TreeAttribute array length "
                            + count
                            + " at byte "
                            + reader.position()
            );
        }

        return count;
    }

    private static void skipFixedArray(
            DotNetBinaryReader reader,
            int count,
            int elementSize
    ) {
        long byteCount = (long) count * elementSize;

        if (byteCount > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "TreeAttribute array is too large"
            );
        }

        reader.skip((int) byteCount);
    }
}