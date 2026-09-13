package cartographer.save;

import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;

import java.util.Objects;

public record SelectiveChunkVisit(
        ChunkPosition position,
        SelectiveChunkVisitStatus status,
        ParsedChunk chunk,
        String error
) {
    public SelectiveChunkVisit {
        Objects.requireNonNull(position, "chunk position is required");
        Objects.requireNonNull(status, "chunk visit status is required");
        if (status == SelectiveChunkVisitStatus.DECODED && chunk == null) {
            throw new IllegalArgumentException("decoded visits require a chunk");
        }
        if (status != SelectiveChunkVisitStatus.DECODED && chunk != null) {
            throw new IllegalArgumentException("only decoded visits contain chunks");
        }
        if (status == SelectiveChunkVisitStatus.FAILED
                && (error == null || error.isBlank())) {
            throw new IllegalArgumentException("failed visits require an error");
        }
        if (status != SelectiveChunkVisitStatus.FAILED && error != null) {
            throw new IllegalArgumentException("only failed visits contain errors");
        }
    }

    public static SelectiveChunkVisit decoded(
            ChunkPosition position,
            ParsedChunk chunk
    ) {
        return new SelectiveChunkVisit(
                position,
                SelectiveChunkVisitStatus.DECODED,
                Objects.requireNonNull(chunk, "chunk is required"),
                null
        );
    }

    public static SelectiveChunkVisit paletteRejected(ChunkPosition position) {
        return new SelectiveChunkVisit(
                position,
                SelectiveChunkVisitStatus.PALETTE_REJECTED,
                null,
                null
        );
    }

    public static SelectiveChunkVisit failed(
            ChunkPosition position,
            String error
    ) {
        return new SelectiveChunkVisit(
                position,
                SelectiveChunkVisitStatus.FAILED,
                null,
                Objects.requireNonNull(error, "error is required")
        );
    }

    public static SelectiveChunkVisit missing(ChunkPosition position) {
        return new SelectiveChunkVisit(
                position,
                SelectiveChunkVisitStatus.MISSING,
                null,
                null
        );
    }
}
