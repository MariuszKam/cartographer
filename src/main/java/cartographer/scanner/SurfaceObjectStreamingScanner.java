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
            this.wantedBlockIds = Arrays.copyOf(
                    Objects.requireNonNull(wantedBlockIds, "wanted block IDs are required"),
                    wantedBlockIds.length);
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
                        int positionIndex = tile.candidateChunkIndexAt(cell, candidate);
                        if ((positionStatuses[positionIndex] & 2) != 0) {
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
                            int count = tile.candidateCountAt(cell);
                            for (int candidate = 0; candidate < count; candidate++) {
                                if (tile.candidateChunkIndexAt(cell, candidate) != positionIndex) continue;
                                int worldY = tile.candidateYAt(cell, candidate);
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
            for (int index = 1; index < observationCount; index++) {
                int x = observationX[index], y = observationY[index], z = observationZ[index], id = observationBlockIds[index];
                int cursor = index - 1;
                while (cursor >= 0 && compare(z, x, y, id,
                        observationZ[cursor], observationX[cursor], observationY[cursor], observationBlockIds[cursor]) < 0) {
                    observationX[cursor + 1] = observationX[cursor]; observationY[cursor + 1] = observationY[cursor];
                    observationZ[cursor + 1] = observationZ[cursor]; observationBlockIds[cursor + 1] = observationBlockIds[cursor--];
                }
                observationX[cursor + 1] = x; observationY[cursor + 1] = y;
                observationZ[cursor + 1] = z; observationBlockIds[cursor + 1] = id;
            }
        }

        private int compare(int z1, int x1, int y1, int id1, int z2, int x2, int y2, int id2) {
            int result = Integer.compare(z1, z2); if (result != 0) return result;
            result = Integer.compare(x1, x2); if (result != 0) return result;
            result = Integer.compare(y1, y2); if (result != 0) return result;
            return Integer.compare(id1, id2);
        }

        private void ensureMutable() {
            if (finished) throw new IllegalStateException("object scan is finished");
        }
    }
}
