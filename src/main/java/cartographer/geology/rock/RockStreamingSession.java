package cartographer.geology.rock;

import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;
import cartographer.model.WorldPosition;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Standalone completion-order-independent streaming UPPER ROCK analyzer. */
public final class RockStreamingSession {
    private final WorldPosition center;
    private final int radius;
    private final int minY;
    private final int maxYExclusive;
    private final int dimension;
    private final RockCircleGeometry geometry;
    private final RockMapBuilder builder;
    private final int chunkMinX;
    private final int chunkMaxX;
    private final int chunkMinZ;
    private final int chunkMaxZ;
    private final int chunkMinY;
    private final int chunkMaxY;
    private final int verticalChunkCount;
    private final int wordsPerHorizontalColumn;
    private final long[] terminalSeenWords;
    private final long[] availableWords;
    private final int[] recognizedBlockIds;
    private final int[] recognizedOrdinals;
    private boolean finished;

    private RockStreamingSession(
            WorldPosition center,
            int radius,
            int minY,
            int maxYExclusive,
            int dimension,
            RockCatalog catalog
    ) {
        this.center = Objects.requireNonNull(center, "center is required");
        this.radius = radius;
        this.minY = minY;
        this.maxYExclusive = maxYExclusive;
        this.dimension = dimension;
        if (minY >= maxYExclusive) throw new IllegalArgumentException("Y range must not be empty");
        this.geometry = RockCircleGeometry.from(center, radius);
        int centerX = geometry.centerX();
        int centerZ = geometry.centerZ();
        int size = ChunkCoordinate.SIZE_BLOCKS;
        this.chunkMinX = checkedChunk(Math.floorDiv(Math.subtractExact((long) centerX, radius), size));
        this.chunkMaxX = checkedChunk(Math.floorDiv(Math.addExact((long) centerX, radius), size));
        this.chunkMinZ = checkedChunk(Math.floorDiv(Math.subtractExact((long) centerZ, radius), size));
        this.chunkMaxZ = checkedChunk(Math.floorDiv(Math.addExact((long) centerZ, radius), size));
        this.chunkMinY = checkedChunk(Math.floorDiv((long) minY, size));
        this.chunkMaxY = checkedChunk(Math.floorDiv(Math.subtractExact((long) maxYExclusive, 1L), size));
        long verticalCount = Math.addExact(Math.subtractExact((long) chunkMaxY, chunkMinY), 1L);
        this.verticalChunkCount = Math.toIntExact(verticalCount);
        this.wordsPerHorizontalColumn = Math.toIntExact(Math.addExact(verticalCount, 63L) / 64L);
        long horizontalCount = Math.multiplyExact(
                Math.addExact((long) chunkMaxX - chunkMinX, 1L),
                Math.addExact((long) chunkMaxZ - chunkMinZ, 1L)
        );
        long planeWords = Math.multiplyExact(horizontalCount, wordsPerHorizontalColumn);
        int planeLength = Math.toIntExact(planeWords);
        this.terminalSeenWords = new long[planeLength];
        this.availableWords = new long[planeLength];
        this.builder = new RockMapBuilder(center, radius, minY, maxYExclusive,
                RockMapMode.UPPER_ROCK, catalog);
        this.builder.initializeAllCellsPresent();
        List<Integer> blockIds = new ArrayList<>(catalog.rockBlockIds());
        blockIds.sort(Integer::compareTo);
        this.recognizedBlockIds = new int[blockIds.size()];
        this.recognizedOrdinals = new int[blockIds.size()];
        List<RockIdentity> ordinals = builder.ordinalTableForSession();
        for (int i = 0; i < blockIds.size(); i++) {
            int blockId = blockIds.get(i);
            RockIdentity identity = catalog.findByBlockId(blockId).orElseThrow();
            recognizedBlockIds[i] = blockId;
            recognizedOrdinals[i] = ordinalFor(ordinals, identity.code());
        }
    }

    public static RockStreamingSession open(
            WorldPosition center,
            int radius,
            int minY,
            int maxYExclusive,
            int dimension,
            RockCatalog catalog
    ) {
        return new RockStreamingSession(center, radius, minY, maxYExclusive, dimension, catalog);
    }

    public static RockStreamingSession open(
            WorldPosition center, int radius, int minY, int maxYExclusive,
            int dimension, RockMapMode mode, RockCatalog catalog
    ) {
        if (mode != RockMapMode.UPPER_ROCK) {
            throw new IllegalArgumentException("Checkpoint D supports UPPER_ROCK only");
        }
        return open(center, radius, minY, maxYExclusive, dimension, catalog);
    }

    public void accept(SelectiveChunkVisit visit) {
        if (finished) throw new IllegalStateException("ROCK streaming session is finished");
        Objects.requireNonNull(visit, "chunk visit is required");
        validatePosition(visit);
        if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
            ParsedChunk chunk = visit.chunk();
            if (!chunk.coordinate().equals(new ChunkCoordinate(
                    visit.position().x(), visit.position().y(), visit.position().z()))) {
                throw new IllegalArgumentException("decoded chunk coordinate does not match visit position");
            }
            if (chunk.sizeX() <= 0 || chunk.sizeY() <= 0 || chunk.sizeZ() <= 0) {
                throw new IllegalArgumentException("decoded chunk dimensions must be positive");
            }
        }
        int word = coverageWord(visit.position().x(), visit.position().y(), visit.position().z());
        long bit = coverageBit(visit.position().y());
        if ((terminalSeenWords[word] & bit) != 0) {
            throw new IllegalArgumentException("duplicate terminal ROCK chunk visit");
        }
        terminalSeenWords[word] |= bit;
        if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
            ParsedChunk chunk = visit.chunk();
            availableWords[word] |= bit;
            consumeDecoded(chunk, visit.position().x(), visit.position().z());
        } else if (visit.status() == SelectiveChunkVisitStatus.PALETTE_REJECTED) {
            availableWords[word] |= bit;
        }
    }

    public RockMap finish() {
        if (finished) throw new IllegalStateException("ROCK streaming session is already finished");
        finished = true;
        for (int row = 0; row < geometry.rowCount(); row++) {
            int worldZ = geometry.worldZForRow(row);
            int startX = geometry.rowStartX(row);
            for (int offset = 0; offset < geometry.rowLength(row); offset++) {
                int worldX = Math.addExact(startX, offset);
                int index = Math.toIntExact(geometry.rowOffset(row) + offset);
                RockColumnState state = builder.hasCandidate(index)
                        ? coverageAboveCandidate(index, worldX, worldZ)
                        : coverageForWholeRange(worldX, worldZ);
                builder.finalizeCell(index, state);
            }
        }
        return builder.finish();
    }

    private void consumeDecoded(ParsedChunk chunk, int chunkX, int chunkZ) {
        if (chunk.sizeX() <= 0 || chunk.sizeY() <= 0 || chunk.sizeZ() <= 0) {
            throw new IllegalArgumentException("decoded chunk dimensions must be positive");
        }
        long startY = Math.max((long) minY, chunk.minY());
        long endY = Math.min((long) maxYExclusive, Math.addExact((long) chunk.minY(), chunk.sizeY()));
        if (startY >= endY) return;
        long chunkStartX = Math.multiplyExact((long) chunkX, ChunkCoordinate.SIZE_BLOCKS);
        long chunkEndX = Math.addExact(chunkStartX, Math.min(chunk.sizeX(), ChunkCoordinate.SIZE_BLOCKS));
        long chunkStartZ = Math.multiplyExact((long) chunkZ, ChunkCoordinate.SIZE_BLOCKS);
        long chunkEndZ = Math.addExact(chunkStartZ, Math.min(chunk.sizeZ(), ChunkCoordinate.SIZE_BLOCKS));
        for (int row = 0; row < geometry.rowCount(); row++) {
            int worldZ = geometry.worldZForRow(row);
            if (worldZ < chunkStartZ || worldZ >= chunkEndZ) continue;
            long startX = Math.max((long) geometry.rowStartX(row), chunkStartX);
            long endX = Math.min(
                    Math.addExact((long) geometry.rowStartX(row), geometry.rowLength(row)),
                    chunkEndX
            );
            for (long worldX = startX; worldX < endX; worldX++) {
                int index = Math.toIntExact(geometry.rowOffset(row) + worldX - geometry.rowStartX(row));
                for (long worldY = endY - 1; worldY >= startY; worldY--) {
                    int blockId = chunk.blockIdAt(
                            Math.toIntExact(worldX - chunkStartX),
                            Math.toIntExact(worldY - chunk.minY()),
                            Math.toIntExact((long) worldZ - chunkStartZ)
                    );
                    int ordinal = ordinalFor(blockId);
                    if (ordinal != 0) {
                        builder.updateCandidate(index, ordinal, Math.toIntExact(worldY));
                        break;
                    }
                }
            }
        }
    }

    private RockColumnState coverageAboveCandidate(int index, int worldX, int worldZ) {
        int candidateY = builder.candidateY(index);
        long firstY = Math.addExact((long) candidateY, 1L);
        long firstRow = Math.max(chunkMinY, Math.floorDiv(firstY, ChunkCoordinate.SIZE_BLOCKS));
        int chunkX = Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS);
        int chunkZ = Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS);
        for (long row = firstRow; row <= chunkMaxY; row++) {
            if (!available(chunkX, chunkZ, Math.toIntExact(row))) return RockColumnState.UNAVAILABLE;
        }
        return RockColumnState.OBSERVED;
    }

    private RockColumnState coverageForWholeRange(int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, ChunkCoordinate.SIZE_BLOCKS);
        int chunkZ = Math.floorDiv(worldZ, ChunkCoordinate.SIZE_BLOCKS);
        for (int row = chunkMinY; row <= chunkMaxY; row++) {
            if (!available(chunkX, chunkZ, row)) return RockColumnState.UNAVAILABLE;
        }
        return RockColumnState.NO_ROCK;
    }

    private boolean available(int chunkX, int chunkZ, int chunkY) {
        int word = coverageWord(chunkX, chunkY, chunkZ);
        return (availableWords[word] & coverageBit(chunkY)) != 0;
    }

    private int coverageWord(int chunkX, int chunkY, int chunkZ) {
        long horizontal = Math.addExact(
                Math.multiplyExact((long) chunkZ - chunkMinZ, (long) chunkMaxX - chunkMinX + 1L),
                (long) chunkX - chunkMinX
        );
        long word = Math.addExact(
                Math.multiplyExact(horizontal, wordsPerHorizontalColumn),
                Math.floorDiv((long) chunkY - chunkMinY, 64L)
        );
        return Math.toIntExact(word);
    }

    private long coverageBit(int chunkY) {
        return 1L << Math.floorMod((long) chunkY - chunkMinY, 64L);
    }

    private void validatePosition(SelectiveChunkVisit visit) {
        if (visit.position().dimension() != dimension) throw new IllegalArgumentException("chunk visit dimension mismatch");
        if (visit.position().x() < chunkMinX || visit.position().x() > chunkMaxX
                || visit.position().z() < chunkMinZ || visit.position().z() > chunkMaxZ
                || visit.position().y() < chunkMinY || visit.position().y() > chunkMaxY) {
            throw new IllegalArgumentException("chunk visit is outside requested ROCK range");
        }
    }

    private int ordinalFor(int blockId) {
        int low = 0;
        int high = recognizedBlockIds.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int candidate = recognizedBlockIds[mid];
            if (candidate < blockId) low = mid + 1;
            else if (candidate > blockId) high = mid - 1;
            else return recognizedOrdinals[mid];
        }
        return 0;
    }

    private static int ordinalFor(List<RockIdentity> table, String code) {
        for (int i = 0; i < table.size(); i++) if (table.get(i).code().equals(code)) return i + 1;
        throw new IllegalArgumentException("catalog ordinal is missing: " + code);
    }

    private static int checkedChunk(long value) {
        return Math.toIntExact(value);
    }
}
