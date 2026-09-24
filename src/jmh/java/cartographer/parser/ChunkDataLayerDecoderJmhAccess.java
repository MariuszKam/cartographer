package cartographer.parser;

import cartographer.model.DecodedChunkLayer;

/**
 * JMH-only access to package-private decoder entry points.
 *
 * <p>Keeps benchmark code off the production public API surface while allowing
 * historical microbenchmarks to exercise the canonical owned decode path.</p>
 */
public final class ChunkDataLayerDecoderJmhAccess {
    private ChunkDataLayerDecoderJmhAccess() {
    }

    public static DecodedChunkLayer decodeOwned(
            ChunkDataLayerDecoder decoder,
            byte[] payload,
            int savedCompressionVersion
    ) {
        return decoder.decodeOwned(payload, savedCompressionVersion);
    }
}
