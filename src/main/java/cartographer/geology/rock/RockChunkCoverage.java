package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class RockChunkCoverage {
    private final Set<ChunkCoordinate> availableChunks;

    private RockChunkCoverage(Set<ChunkCoordinate> availableChunks) {
        this.availableChunks = Set.copyOf(availableChunks);
    }

    public static RockChunkCoverage fromChunkCoordinates(
            Collection<ChunkCoordinate> coordinates
    ) {
        Set<ChunkCoordinate> available = new HashSet<>();
        for (ChunkCoordinate coordinate : coordinates) {
            available.add(
                    Objects.requireNonNull(
                            coordinate,
                            "chunk coverage cannot contain null"
                    )
            );
        }
        return new RockChunkCoverage(available);
    }

    static RockChunkCoverage fromParsedChunks(Collection<ParsedChunk> chunks) {
        Set<ChunkCoordinate> coordinates = new HashSet<>();
        for (ParsedChunk chunk : chunks) {
            coordinates.add(
                    Objects.requireNonNull(
                            chunk,
                            "chunks must not contain null"
                    ).coordinate()
            );
        }
        return new RockChunkCoverage(coordinates);
    }

    Set<ChunkCoordinate> availableChunks() {
        return availableChunks;
    }
}
