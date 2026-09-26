package cartographer.parser;

import cartographer.model.ParsedChunk;

import java.util.Objects;
import java.util.Optional;

/**
 * Allocation-light result for selective block-layer decoding.
 */
public record SelectiveChunkParseResult(
        boolean payloadParsed,
        boolean paletteRejected,
        Optional<ParsedChunk> chunk,
        Optional<String> error
) {
    public SelectiveChunkParseResult {
        Objects.requireNonNull(chunk, "chunk is required");
        Objects.requireNonNull(error, "error is required");

        if (paletteRejected) {
            if (!payloadParsed || chunk.isPresent() || error.isPresent()) {
                throw new IllegalArgumentException(
                        "palette rejection requires parsed payload and no result/error"
                );
            }
        } else if (chunk.isPresent() == error.isPresent()) {
            throw new IllegalArgumentException(
                    "selective parse must contain exactly one chunk or error"
            );
        }
    }

    public static SelectiveChunkParseResult payloadFailure(String error) {
        return new SelectiveChunkParseResult(
                false,
                false,
                Optional.empty(),
                Optional.of(requireError(error))
        );
    }

    public static SelectiveChunkParseResult decodeFailure(String error) {
        return new SelectiveChunkParseResult(
                true,
                false,
                Optional.empty(),
                Optional.of(requireError(error))
        );
    }

    public static SelectiveChunkParseResult rejected() {
        return new SelectiveChunkParseResult(
                true,
                true,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static SelectiveChunkParseResult decoded(ParsedChunk chunk) {
        return new SelectiveChunkParseResult(
                true,
                false,
                Optional.of(Objects.requireNonNull(chunk, "chunk is required")),
                Optional.empty()
        );
    }

    private static String requireError(String error) {
        Objects.requireNonNull(error, "error is required");
        if (error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        return error;
    }
}
