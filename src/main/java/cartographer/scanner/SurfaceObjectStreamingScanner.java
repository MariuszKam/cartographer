package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ChunkPosition;
import cartographer.model.ParsedChunk;
import cartographer.save.SelectiveChunkVisit;
import cartographer.save.SelectiveChunkVisitStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Consumes selective visits immediately into primitive observation arrays. */
public final class SurfaceObjectStreamingScanner {
    public Session begin(
            SurfaceObjectCompactPlan plan,
            int[] wantedBlockIds
    ) {
        return new Session(plan, wantedBlockIds);
    }

    public static final class Session {
        private static final byte VISITED = 1;
        private static final byte OBSERVED = 1 << 1;
        private final SurfaceObjectCompactPlan plan;
        private final int[] wantedBlockIds;
        private final Map<ChunkPosition, Integer> positionIndexes = new HashMap<>();
        private final byte[] positionStatuses;
        private final byte[][] targetStates;
        private int[] observationX = new int[16];
        private int[] observationY = new int[16];
        private int[] observationZ = new int[16];
        private int[] observationBlockIds = new int[16];
        private int observationCount;
        private boolean finished;

        private Session(SurfaceObjectCompactPlan plan, int[] wantedBlockIds) {
            this.plan = Objects.requireNonNull(plan, "plan is required");
            Objects.requireNonNull(
                    wantedBlockIds,
                    "wanted block IDs are required"
            );
            if (wantedBlockIds.length == 0) {
                throw new IllegalArgumentException(
                        "wanted block IDs cannot be empty"
                );
            }
            this.wantedBlockIds = Arrays.copyOf(
                    wantedBlockIds,
                    wantedBlockIds.length
            );
            Arrays.sort(this.wantedBlockIds);
            this.positionStatuses = new byte[plan.chunkPositions().size()];
            this.targetStates = new byte[plan.tileCount()][];
            for (int index = 0; index < plan.chunkPositions().size(); index++) {
                positionIndexes.put(plan.chunkPositions().get(index), index);
            }
            for (int index = 0; index < plan.tileCount(); index++) {
                this.targetStates[index] = plan.tileAt(index).newTargetState();
            }
        }

        public void accept(SelectiveChunkVisit visit) {
            ensureMutable();
            Objects.requireNonNull(visit, "visit is required");
            Integer positionIndex = positionIndexes.get(visit.position());
            if (positionIndex == null || (positionStatuses[positionIndex] & VISITED) != 0) {
                return;
            }
            boolean available = visit.status() == SelectiveChunkVisitStatus.DECODED
                    || visit.status() == SelectiveChunkVisitStatus.PALETTE_REJECTED;
            positionStatuses[positionIndex] = (byte) (VISITED | (available ? 0 : 2));
            if (visit.status() == SelectiveChunkVisitStatus.DECODED) {
                consumeDecoded(positionIndex, visit.chunk());
            }
        }

        public SurfaceObjectCompactScanResult finish() {
            ensureMutable();
            finished = true;
            int unavailable = 0;
            int observedTargets = 0;
            int notObserved = 0;
            for (int tileIndex = 0; tileIndex < plan.tileCount(); tileIndex++) {
                SurfaceObjectCompactPlan.Tile tile = plan.tileAt(tileIndex);
                byte[] states = targetStates[tileIndex];
                for (int cell = 0; cell < states.length; cell++) {
                    int count = tile.candidateCountAt(cell);
                    if (count == 0) continue;
                    if ((states[cell] & OBSERVED) != 0) {
                        observedTargets++;
                        continue;
                    }
                    boolean targetUnavailable = false;
                    for (int candidate = 0; candidate < count; candidate++) {
                        int worldX = tile.coordinate().x() * cartographer.model.MapChunk.SIZE
                                + cell % cartographer.model.MapChunk.SIZE;
                        int worldZ = tile.coordinate().z() * cartographer.model.MapChunk.SIZE
                                + cell / cartographer.model.MapChunk.SIZE;
                        int positionIndex = plan.chunkPositionIndexAt(
                                worldX, tile.candidateYAt(cell, candidate), worldZ);
                        if (isUnavailable(positionIndex)) {
                            targetUnavailable = true;
                            break;
                        }
                    }
                    if (targetUnavailable) unavailable++;
                    else notObserved++;
                }
            }
            sortObservations();
            return new SurfaceObjectCompactScanResult(
                    Arrays.copyOf(observationX, observationCount),
                    Arrays.copyOf(observationY, observationCount),
                    Arrays.copyOf(observationZ, observationCount),
                    Arrays.copyOf(observationBlockIds, observationCount),
                    plan.plannedTargetCount(), unavailable, observedTargets, notObserved);
        }

        private void consumeDecoded(int positionIndex, ParsedChunk chunk) {
            int firstWorldX = chunk.worldX(0);
            int firstWorldZ = chunk.worldZ(0);
            int lastWorldX = chunk.worldX(chunk.sizeX() - 1);
            int lastWorldZ = chunk.worldZ(chunk.sizeZ() - 1);
            int firstTileX = Math.floorDiv(firstWorldX, cartographer.model.MapChunk.SIZE);
            int lastTileX = Math.floorDiv(lastWorldX, cartographer.model.MapChunk.SIZE);
            int firstTileZ = Math.floorDiv(firstWorldZ, cartographer.model.MapChunk.SIZE);
            int lastTileZ = Math.floorDiv(lastWorldZ, cartographer.model.MapChunk.SIZE);
            for (int tileZ = firstTileZ; tileZ <= lastTileZ; tileZ++) {
                for (int tileX = firstTileX; tileX <= lastTileX; tileX++) {
                    int tileIndex = plan.tileIndexAt(
                            new cartographer.model.MapChunkCoordinate(tileX, tileZ));
                    if (tileIndex < 0) continue;
                    SurfaceObjectCompactPlan.Tile tile = plan.tileAt(tileIndex);
                    byte[] states = targetStates[tileIndex];
                    for (int localZ = 0; localZ < cartographer.model.MapChunk.SIZE; localZ++) {
                        for (int localX = 0; localX < cartographer.model.MapChunk.SIZE; localX++) {
                            int worldX = tile.coordinate().x() * cartographer.model.MapChunk.SIZE + localX;
                            int worldZ = tile.coordinate().z() * cartographer.model.MapChunk.SIZE + localZ;
                            if (worldX < firstWorldX || worldX > lastWorldX
                                    || worldZ < firstWorldZ || worldZ > lastWorldZ) continue;
                            int cell = localZ * cartographer.model.MapChunk.SIZE + localX;
                            for (int worldY = tile.firstCandidateY(cell);
                                 worldY < tile.lastCandidateYExclusive(cell); worldY++) {
                                if (!tile.isCandidateY(cell, worldY)
                                        || plan.chunkPositionIndexAt(worldX, worldY, worldZ) != positionIndex) continue;
                                int localY = worldY - chunk.minY();
                                if (localY < 0 || localY >= chunk.sizeY()) continue;
                                int blockId = chunk.blockIdAt(
                                        worldX - firstWorldX, localY, worldZ - firstWorldZ);
                                if (Arrays.binarySearch(wantedBlockIds, blockId) >= 0) {
                                    states[cell] |= OBSERVED;
                                    addObservation(worldX, worldY, worldZ, blockId);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void addObservation(int x, int y, int z, int blockId) {
            if (observationCount == observationX.length) {
                int next = Math.multiplyExact(observationCount, 2);
                observationX = Arrays.copyOf(observationX, next);
                observationY = Arrays.copyOf(observationY, next);
                observationZ = Arrays.copyOf(observationZ, next);
                observationBlockIds = Arrays.copyOf(observationBlockIds, next);
            }
            observationX[observationCount] = x;
            observationY[observationCount] = y;
            observationZ[observationCount] = z;
            observationBlockIds[observationCount++] = blockId;
        }

        private void sortObservations() {
            // Heap sort keeps the four primitive payload arrays in lockstep and guarantees O(m log m).
            for (int root = observationCount / 2 - 1; root >= 0; root--) siftDown(root, observationCount);
            for (int end = observationCount - 1; end > 0; end--) {
                swap(0, end);
                siftDown(0, end);
            }
        }

        private void siftDown(int root, int size) {
            while (root * 2 + 1 < size) {
                int child = root * 2 + 1;
                if (child + 1 < size && compareAt(child, child + 1) < 0) child++;
                if (compareAt(root, child) >= 0) return;
                swap(root, child);
                root = child;
            }
        }

        private int compareAt(int left, int right) {
            return compare(observationZ[left], observationX[left], observationY[left], observationBlockIds[left],
                    observationZ[right], observationX[right], observationY[right], observationBlockIds[right]);
        }

        private void swap(int left, int right) {
            int value = observationX[left]; observationX[left] = observationX[right]; observationX[right] = value;
            value = observationY[left]; observationY[left] = observationY[right]; observationY[right] = value;
            value = observationZ[left]; observationZ[left] = observationZ[right]; observationZ[right] = value;
            value = observationBlockIds[left]; observationBlockIds[left] = observationBlockIds[right]; observationBlockIds[right] = value;
        }

        private int compare(int z1, int x1, int y1, int id1, int z2, int x2, int y2, int id2) {
            int result = Integer.compare(z1, z2); if (result != 0) return result;
            result = Integer.compare(x1, x2); if (result != 0) return result;
            result = Integer.compare(y1, y2); if (result != 0) return result;
            return Integer.compare(id1, id2);
        }

        private boolean isUnavailable(int positionIndex) {
            return positionIndex < 0
                    || (positionStatuses[positionIndex] & 2) != 0
                    || (positionStatuses[positionIndex] & VISITED) == 0;
        }

        private void ensureMutable() {
            if (finished) throw new IllegalStateException("object scan is finished");
        }
    }
}
