package cartographer.snapshot;

import cartographer.geology.rock.RockCatalog;
import cartographer.geology.rock.RockColumnState;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldMetadata;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded snapshot builder for full-height UPPER_ROCK mapchunk tiles.
 *
 * <p>Availability semantics intentionally mirror {@code RockStreamingSession}:
 * decoded and palette-rejected chunks are available; missing/failed chunks are
 * unavailable. A highest-rock candidate is OBSERVED only when all source
 * chunks above that candidate are available.</p>
 */
public final class UpperRockTileBatchIndexer {
    private final WorldMetadata metadata;
    private final int verticalChunkCount;
    private final int[] wantedBlockIds;
    private final Map<MapChunkCoordinate, TileState> states;
    private boolean finished;

    public UpperRockTileBatchIndexer(
            WorldMetadata metadata,
            Collection<MapChunkCoordinate> coordinates,
            RockCatalog catalog
    ) {
        this.metadata = Objects.requireNonNull(
                metadata,
                "metadata is required"
        );
        Objects.requireNonNull(coordinates, "coordinates are required");
        Objects.requireNonNull(catalog, "catalog is required");
        if (catalog.rockBlockIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "ROCK catalog must contain natural rock IDs"
            );
        }
        this.verticalChunkCount = Math.toIntExact(
                Math.floorDiv(
                        Math.addExact(
                                metadata.mapSizeY(),
                                ChunkCoordinate.SIZE_BLOCKS - 1L
                        ),
                        ChunkCoordinate.SIZE_BLOCKS
                )
        );
        if (verticalChunkCount <= 0) {
            throw new IllegalArgumentException(
                    "world vertical range must contain at least one chunk"
            );
        }

        this.wantedBlockIds = catalog.rockBlockIds().stream()
                .sorted()
                .mapToInt(Integer::intValue)
                .toArray();

        LinkedHashSet<MapChunkCoordinate> unique = new LinkedHashSet<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            unique.add(Objects.requireNonNull(
                    coordinate,
                    "coordinates cannot contain null"
            ));
        }
        List<MapChunkCoordinate> ordered = unique.stream()
                .sorted(
                        Comparator.comparingInt(MapChunkCoordinate::z)
                                .thenComparingInt(MapChunkCoordinate::x)
                )
                .toList();
        this.states = new LinkedHashMap<>();
        for (MapChunkCoordinate coordinate : ordered) {
            UpperRockTile.Geometry geometry =
                    UpperRockTile.geometry(coordinate, metadata);
            states.put(
                    coordinate,
                    new TileState(
                            geometry.width(),
                            geometry.height(),
                            verticalChunkCount
                    )
            );
        }
    }

    public int[] wantedBlockIds() {
        return wantedBlockIds.clone();
    }

    public List<ChunkPosition> positions() {
        ensureOpen();
        List<ChunkPosition> result = new ArrayList<>(
                Math.multiplyExact(states.size(), verticalChunkCount)
        );
        for (MapChunkCoordinate coordinate : states.keySet()) {
            for (int chunkY = 0;
                 chunkY < verticalChunkCount;
                 chunkY++) {
                result.add(
                        new ChunkPosition(
                                coordinate.x(),
                                chunkY,
                                coordinate.z(),
                                0
                        )
                );
            }
        }
        return List.copyOf(result);
    }

    public void accept(SelectiveChunkVisit visit) {
        ensureOpen();
        Objects.requireNonNull(visit, "visit is required");
        ChunkPosition position = visit.position();
        if (position.dimension() != 0) {
            throw new IllegalArgumentException(
                    "UPPER_ROCK indexing only supports main-world chunks"
            );
        }
        TileState state = states.get(
                new MapChunkCoordinate(position.x(), position.z())
        );
        if (state == null
                || position.y() < 0
                || position.y() >= verticalChunkCount) {
            throw new IllegalArgumentException(
                    "ROCK visit is outside the indexed batch"
            );
        }
        if (state.terminalSeen[position.y()]) {
            throw new IllegalArgumentException(
                    "duplicate terminal ROCK visit"
            );
        }
        state.terminalSeen[position.y()] = true;

        if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
            ParsedChunk chunk = visit.chunk();
            ChunkCoordinate expected = new ChunkCoordinate(
                    position.x(),
                    position.y(),
                    position.z()
            );
            if (!expected.equals(chunk.coordinate())) {
                throw new IllegalArgumentException(
                        "decoded ROCK chunk coordinate does not match visit"
                );
            }
            state.available[position.y()] = true;
            consumeDecoded(state, chunk);
        } else if (visit.status()
                == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
            state.available[position.y()] = true;
        }
    }

    public List<UpperRockTile> finish() {
        ensureOpen();
        finished = true;
        List<UpperRockTile> result = new ArrayList<>(states.size());
        for (Map.Entry<MapChunkCoordinate, TileState> entry :
                states.entrySet()) {
            MapChunkCoordinate coordinate = entry.getKey();
            TileState state = entry.getValue();
            byte[] statesByCell = new byte[state.cellCount];
            int[] finalBlockIds = new int[state.cellCount];
            int[] finalY = new int[state.cellCount];
            Arrays.fill(finalBlockIds, -1);
            Arrays.fill(finalY, -1);

            for (int index = 0; index < state.cellCount; index++) {
                int candidateY = state.candidateY[index];
                RockColumnState finalState;
                if (candidateY >= 0) {
                    finalState = coverageAboveCandidate(
                            state,
                            candidateY
                    )
                            ? RockColumnState.OBSERVED
                            : RockColumnState.UNAVAILABLE;
                    if (finalState == RockColumnState.OBSERVED) {
                        finalBlockIds[index] =
                                state.candidateBlockIds[index];
                        finalY[index] = candidateY;
                    }
                } else {
                    finalState = wholeRangeAvailable(state)
                            ? RockColumnState.NO_ROCK
                            : RockColumnState.UNAVAILABLE;
                }
                statesByCell[index] =
                        UpperRockTile.encodeState(finalState);
            }

            result.add(
                    new UpperRockTile(
                            coordinate,
                            metadata.mapSizeX(),
                            metadata.mapSizeY(),
                            metadata.mapSizeZ(),
                            state.width,
                            state.height,
                            statesByCell,
                            finalBlockIds,
                            finalY
                    )
            );
        }
        return List.copyOf(result);
    }

    private void consumeDecoded(
            TileState state,
            ParsedChunk chunk
    ) {
        int maxLocalY = Math.min(
                chunk.sizeY(),
                Math.max(
                        0,
                        metadata.mapSizeY() - chunk.minY()
                )
        );
        int maxLocalX = Math.min(state.width, chunk.sizeX());
        int maxLocalZ = Math.min(state.height, chunk.sizeZ());
        if (maxLocalY <= 0 || maxLocalX <= 0 || maxLocalZ <= 0) {
            return;
        }

        for (int localZ = 0; localZ < maxLocalZ; localZ++) {
            for (int localX = 0; localX < maxLocalX; localX++) {
                int cell = localZ * state.width + localX;
                for (int localY = maxLocalY - 1;
                     localY >= 0;
                     localY--) {
                    int blockId = chunk.blockIdAt(
                            localX,
                            localY,
                            localZ
                    );
                    if (!isWantedRock(blockId)) {
                        continue;
                    }
                    int worldY = chunk.worldY(localY);
                    if (worldY > state.candidateY[cell]) {
                        state.candidateY[cell] = worldY;
                        state.candidateBlockIds[cell] = blockId;
                    }
                    break;
                }
            }
        }
    }

    private boolean coverageAboveCandidate(
            TileState state,
            int candidateY
    ) {
        long firstY = Math.addExact(candidateY, 1L);
        int firstChunk = Math.toIntExact(
                Math.floorDiv(firstY, ChunkCoordinate.SIZE_BLOCKS)
        );
        firstChunk = Math.max(0, firstChunk);
        for (int chunkY = firstChunk;
             chunkY < verticalChunkCount;
             chunkY++) {
            if (!state.available[chunkY]) {
                return false;
            }
        }
        return true;
    }

    private boolean wholeRangeAvailable(TileState state) {
        for (boolean available : state.available) {
            if (!available) return false;
        }
        return true;
    }

    private boolean isWantedRock(int blockId) {
        return Arrays.binarySearch(wantedBlockIds, blockId) >= 0;
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException(
                    "UPPER_ROCK batch indexer is already finished"
            );
        }
    }

    private static final class TileState {
        private final int width;
        private final int height;
        private final int cellCount;
        private final int[] candidateBlockIds;
        private final int[] candidateY;
        private final boolean[] terminalSeen;
        private final boolean[] available;

        private TileState(
                int width,
                int height,
                int verticalChunkCount
        ) {
            this.width = width;
            this.height = height;
            this.cellCount = Math.multiplyExact(width, height);
            this.candidateBlockIds = new int[cellCount];
            this.candidateY = new int[cellCount];
            Arrays.fill(candidateBlockIds, -1);
            Arrays.fill(candidateY, -1);
            this.terminalSeen = new boolean[verticalChunkCount];
            this.available = new boolean[verticalChunkCount];
        }
    }
}
