package cartographer.save;

import java.nio.charset.StandardCharsets;

public final class DotNetBinaryReader {

    private final byte[] data;
    private int position;

    public DotNetBinaryReader(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null");
        }

        this.data = data;
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return data.length - position;
    }

    public int readUnsignedByte() {
        require(1);

        return Byte.toUnsignedInt(
                data[position++]
        );
    }

    public boolean readBoolean() {
        return readUnsignedByte() != 0;
    }

    public int readUnsignedShortLE() {
        require(2);

        int result =
                Byte.toUnsignedInt(data[position])
                        | (Byte.toUnsignedInt(data[position + 1]) << 8);

        position += 2;

        return result;
    }

    public int readInt32LE() {
        require(4);

        int result =
                Byte.toUnsignedInt(data[position])
                        | (Byte.toUnsignedInt(data[position + 1]) << 8)
                        | (Byte.toUnsignedInt(data[position + 2]) << 16)
                        | (Byte.toUnsignedInt(data[position + 3]) << 24);

        position += 4;

        return result;
    }

    public long readInt64LE() {
        require(8);

        long result = 0;

        for (int index = 0; index < 8; index++) {
            result |= (long) Byte.toUnsignedInt(data[position + index])
                    << (index * 8);
        }

        position += 8;

        return result;
    }

    public float readFloatLE() {
        return Float.intBitsToFloat(
                readInt32LE()
        );
    }

    public double readDoubleLE() {
        return Double.longBitsToDouble(
                readInt64LE()
        );
    }

    public String readDotNetString() {
        int byteLength = read7BitEncodedInt();

        if (byteLength < 0) {
            throw new IllegalStateException(
                    "Negative .NET string length at byte " + position
            );
        }

        require(byteLength);

        String result = new String(
                data,
                position,
                byteLength,
                StandardCharsets.UTF_8
        );

        position += byteLength;

        return result;
    }

    public void skip(int byteCount) {
        require(byteCount);
        position += byteCount;
    }

    private int read7BitEncodedInt() {
        int result = 0;
        int shift = 0;

        for (int index = 0; index < 5; index++) {
            int current = readUnsignedByte();

            if (index == 4 && (current & 0xF0) != 0) {
                throw new IllegalStateException(
                        "Invalid .NET 7-bit encoded integer"
                );
            }

            result |= (current & 0x7F) << shift;

            if ((current & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalStateException(
                "Invalid .NET 7-bit encoded integer"
        );
    }

    private void require(int byteCount) {
        if (byteCount < 0 || position + byteCount > data.length) {
            throw new IllegalStateException(
                    "Unexpected end of binary data at byte "
                            + position
                            + ", requested "
                            + byteCount
                            + " bytes but only "
                            + remaining()
                            + " remain"
            );
        }
    }
}