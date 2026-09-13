package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
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

    public static RockChunkCoverage fromChunkPositions(
            Collection<ChunkPosition> positions
    ) {
        Set<ChunkCoordinate> coordinates = new HashSet<>();
        for (ChunkPosition position : positions) {
            ChunkPosition required = Objects.requireNonNull(
                    position,
                    "chunk coverage cannot contain null"
            );
            coordinates.add(
                    new ChunkCoordinate(
                            required.x(),
                            required.y(),
                            required.z()
                    )
            );
        }
        return new RockChunkCoverage(coordinates);
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

    public boolean contains(ChunkCoordinate coordinate) {
        return availableChunks.contains(
                Objects.requireNonNull(coordinate, "chunk coordinate is required")
        );
    }

    Set<ChunkCoordinate> availableChunks() {
        return availableChunks;
    }
}
