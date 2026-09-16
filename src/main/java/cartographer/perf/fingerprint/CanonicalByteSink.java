package cartographer.perf.fingerprint;

@FunctionalInterface
interface CanonicalByteSink {
    void writeByte(int value);

    default void writeBytes(byte[] bytes, int offset, int length) {
        for (int index = offset; index < offset + length; index++) {
            writeByte(bytes[index] & 0xff);
        }
    }
}
