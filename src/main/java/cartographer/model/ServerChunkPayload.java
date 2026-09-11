package cartographer.model;

public record ServerChunkPayload(
        byte[] blocksCompressed,
        byte[] liquidsCompressed,
        int savedCompressionVersion
) {
    public ServerChunkPayload {
        blocksCompressed =
                blocksCompressed == null
                        ? new byte[0]
                        : blocksCompressed.clone();

        liquidsCompressed =
                liquidsCompressed == null
                        ? new byte[0]
                        : liquidsCompressed.clone();
    }

    public byte[] blocksCompressed() {
        return blocksCompressed.clone();
    }

    public byte[] liquidsCompressed() {
        return liquidsCompressed.clone();
    }
}
