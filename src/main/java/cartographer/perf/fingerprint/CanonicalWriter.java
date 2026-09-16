package cartographer.perf.fingerprint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Small canonical binary writer. Every value has a type marker and
 * length/count framing where applicable, so concatenation is unambiguous.
 */
public final class CanonicalWriter {
    private static final byte INT = 1;
    private static final byte LONG = 2;
    private static final byte BOOLEAN = 3;
    private static final byte STRING = 4;
    private static final byte ENUM = 5;
    private static final byte BYTES = 6;
    private static final byte SEQUENCE = 7;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public CanonicalWriter writeInt(int value) {
        output.write(INT);
        writeRawInt(value);
        return this;
    }

    public CanonicalWriter writeLong(long value) {
        output.write(LONG);
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
        return this;
    }

    public CanonicalWriter writeBoolean(boolean value) {
        output.write(BOOLEAN);
        output.write(value ? 1 : 0);
        return this;
    }

    public CanonicalWriter writeString(String value) {
        Objects.requireNonNull(value, "value is required");
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.write(STRING);
        writeRawInt(encoded.length);
        output.writeBytes(encoded);
        return this;
    }

    public CanonicalWriter writeEnum(Enum<?> value) {
        Objects.requireNonNull(value, "enum value is required");
        output.write(ENUM);
        writeString(value.getDeclaringClass().getName());
        writeString(value.name());
        return this;
    }

    public CanonicalWriter writeBytes(byte[] value) {
        Objects.requireNonNull(value, "value is required");
        output.write(BYTES);
        writeRawInt(value.length);
        output.writeBytes(value);
        return this;
    }

    /** Writes a sequence count; callers then write elements in semantic order. */
    public CanonicalWriter writeSequenceStart(int elementCount) {
        if (elementCount < 0) {
            throw new IllegalArgumentException("elementCount must not be negative");
        }
        output.write(SEQUENCE);
        writeRawInt(elementCount);
        return this;
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }

    private void writeRawInt(int value) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            output.write((value >>> shift) & 0xff);
        }
    }
}
