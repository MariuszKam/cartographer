package cartographer.perf.fingerprint;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Small canonical binary writer that emits directly to a sink. Every value
 * has a type marker and length/count framing where applicable, so
 * concatenation is unambiguous without retaining the encoded payload.
 */
public final class CanonicalWriter {
    private static final byte INT = 1;
    private static final byte LONG = 2;
    private static final byte BOOLEAN = 3;
    private static final byte STRING = 4;
    private static final byte ENUM = 5;
    private static final byte BYTES = 6;
    private static final byte SEQUENCE = 7;

    private final CanonicalByteSink sink;

    CanonicalWriter(CanonicalByteSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink is required");
    }

    public CanonicalWriter writeInt(int value) {
        writeByte(INT);
        writeRawInt(value);
        return this;
    }

    public CanonicalWriter writeLong(long value) {
        writeByte(LONG);
        for (int shift = 56; shift >= 0; shift -= 8) {
            writeByte((int) (value >>> shift) & 0xff);
        }
        return this;
    }

    public CanonicalWriter writeBoolean(boolean value) {
        writeByte(BOOLEAN);
        writeByte(value ? 1 : 0);
        return this;
    }

    public CanonicalWriter writeString(String value) {
        Objects.requireNonNull(value, "value is required");
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeByte(STRING);
        writeRawInt(encoded.length);
        writeRawBytes(encoded);
        return this;
    }

    public CanonicalWriter writeEnum(Enum<?> value) {
        Objects.requireNonNull(value, "enum value is required");
        writeByte(ENUM);
        writeString(value.getDeclaringClass().getName());
        writeString(value.name());
        return this;
    }

    public CanonicalWriter writeBytes(byte[] value) {
        Objects.requireNonNull(value, "value is required");
        writeByte(BYTES);
        writeRawInt(value.length);
        writeRawBytes(value);
        return this;
    }

    /** Writes a sequence count; callers then write elements in semantic order. */
    public CanonicalWriter writeSequenceStart(int elementCount) {
        if (elementCount < 0) {
            throw new IllegalArgumentException("elementCount must not be negative");
        }
        writeByte(SEQUENCE);
        writeRawInt(elementCount);
        return this;
    }

    private void writeByte(int value) {
        sink.writeByte(value);
    }

    private void writeRawBytes(byte[] value) {
        sink.writeBytes(value, 0, value.length);
    }

    private void writeRawInt(int value) {
        for (int shift = 24; shift >= 0; shift -= 8) {
            writeByte((value >>> shift) & 0xff);
        }
    }
}
