package cartographer.snapshot;

import cartographer.geology.rock.RockColumnState;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.Objects;

/** Compact revision-scoped UPPER_ROCK state for one horizontal mapchunk. */
public final class UpperRockTile {
    private final MapChunkCoordinate coordinate;
    private final int worldSizeX;
    private final int worldSizeY;
    private final int worldSizeZ;
    private final int width;
    private final int height;
    private final byte[] states;
    private final int[] blockIds;
    private final int[] rockY;

    public UpperRockTile(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeY,
            int worldSizeZ,
            int width,
            int height,
            byte[] states,
            int[] blockIds,
            int[] rockY
    ) {
        this(
                coordinate,
                worldSizeX,
                worldSizeY,
                worldSizeZ,
                width,
                height,
                states,
                blockIds,
                rockY,
                true
        );
    }

    private UpperRockTile(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeY,
            int worldSizeZ,
            int width,
            int height,
            byte[] states,
            int[] blockIds,
            int[] rockY,
            boolean copyArrays
    ) {
        this.coordinate = Objects.requireNonNull(
                coordinate,
                "coordinate is required"
        );
        if (worldSizeX <= 0 || worldSizeY <= 0 || worldSizeZ <= 0) {
            throw new IllegalArgumentException(
                    "world dimensions must be positive"
            );
        }
        if (width <= 0 || width > MapChunkCoordinate.SIZE_BLOCKS
                || height <= 0 || height > MapChunkCoordinate.SIZE_BLOCKS) {
            throw new IllegalArgumentException(
                    "ROCK tile dimensions are invalid"
            );
        }
        int expected = Math.multiplyExact(width, height);
        if (states == null || states.length != expected
                || blockIds == null || blockIds.length != expected
                || rockY == null || rockY.length != expected) {
            throw new IllegalArgumentException(
                    "ROCK tile arrays do not match geometry"
            );
        }
        this.states = copyArrays ? states.clone() : states;
        this.blockIds = copyArrays ? blockIds.clone() : blockIds;
        this.rockY = copyArrays ? rockY.clone() : rockY;
        for (int index = 0; index < expected; index++) {
            RockColumnState state = decodeState(this.states[index]);
            if (state == RockColumnState.OBSERVED) {
                if (this.blockIds[index] < 0
                        || this.rockY[index] < 0
                        || this.rockY[index] >= worldSizeY) {
                    throw new IllegalArgumentException(
                            "observed ROCK cell requires valid blockId and Y"
                    );
                }
            } else if (this.blockIds[index] != -1 || this.rockY[index] != -1) {
                throw new IllegalArgumentException(
                        "non-observed ROCK cells cannot contain rock data"
                );
            }
        }
        this.worldSizeX = worldSizeX;
        this.worldSizeY = worldSizeY;
        this.worldSizeZ = worldSizeZ;
        this.width = width;
        this.height = height;
    }

    static UpperRockTile owned(
            MapChunkCoordinate coordinate,
            int worldSizeX,
            int worldSizeY,
            int worldSizeZ,
            int width,
            int height,
            byte[] states,
            int[] blockIds,
            int[] rockY
    ) {
        return new UpperRockTile(
                coordinate,
                worldSizeX,
                worldSizeY,
                worldSizeZ,
                width,
                height,
                states,
                blockIds,
                rockY,
                false
        );
    }

    public MapChunkCoordinate coordinate() {
        return coordinate;
    }

    public int worldSizeX() {
        return worldSizeX;
    }

    public int worldSizeY() {
        return worldSizeY;
    }

    public int worldSizeZ() {
        return worldSizeZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int cellCount() {
        return states.length;
    }

    public RockColumnState stateAt(int localX, int localZ) {
        return decodeState(states[index(localX, localZ)]);
    }

    public int blockIdAt(int localX, int localZ) {
        return blockIds[index(localX, localZ)];
    }

    public int rockYAt(int localX, int localZ) {
        return rockY[index(localX, localZ)];
    }

    public boolean matchesWorld(WorldMetadata metadata) {
        return metadata != null
                && worldSizeX == metadata.mapSizeX()
                && worldSizeY == metadata.mapSizeY()
                && worldSizeZ == metadata.mapSizeZ()
                && geometry(coordinate, metadata).width() == width
                && geometry(coordinate, metadata).height() == height;
    }

    byte[] stateCodesView() {
        return states;
    }

    int[] blockIdsView() {
        return blockIds;
    }

    int[] rockYView() {
        return rockY;
    }

    public static Geometry geometry(
            MapChunkCoordinate coordinate,
            WorldMetadata metadata
    ) {
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(metadata, "metadata is required");
        long startX = Math.multiplyExact(
                (long) coordinate.x(),
                MapChunkCoordinate.SIZE_BLOCKS
        );
        long startZ = Math.multiplyExact(
                (long) coordinate.z(),
                MapChunkCoordinate.SIZE_BLOCKS
        );
        if (startX < 0 || startZ < 0
                || startX >= metadata.mapSizeX()
                || startZ >= metadata.mapSizeZ()) {
            throw new IllegalArgumentException(
                    "mapchunk coordinate is outside world bounds"
            );
        }
        int width = Math.toIntExact(
                Math.min(
                        MapChunkCoordinate.SIZE_BLOCKS,
                        (long) metadata.mapSizeX() - startX
                )
        );
        int height = Math.toIntExact(
                Math.min(
                        MapChunkCoordinate.SIZE_BLOCKS,
                        (long) metadata.mapSizeZ() - startZ
                )
        );
        return new Geometry(width, height);
    }

    static byte encodeState(RockColumnState state) {
        return switch (Objects.requireNonNull(state, "state is required")) {
            case OBSERVED -> 1;
            case NO_ROCK -> 2;
            case UNAVAILABLE -> 3;
        };
    }

    static RockColumnState decodeState(byte code) {
        return switch (code) {
            case 1 -> RockColumnState.OBSERVED;
            case 2 -> RockColumnState.NO_ROCK;
            case 3 -> RockColumnState.UNAVAILABLE;
            default -> throw new IllegalArgumentException(
                    "invalid ROCK tile state code: " + code
            );
        };
    }

    private int index(int localX, int localZ) {
        if (localX < 0 || localX >= width
                || localZ < 0 || localZ >= height) {
            throw new IndexOutOfBoundsException(
                    "ROCK tile coordinate out of bounds"
            );
        }
        return localZ * width + localX;
    }

    public record Geometry(int width, int height) {
        public Geometry {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "ROCK geometry must be positive"
                );
            }
        }
    }

    @Override
    public String toString() {
        return "UpperRockTile[coordinate=" + coordinate
                + ", geometry=" + width + "x" + height
                + ", cells=" + states.length + "]";
    }
}
