package cartographer.perf;

import cartographer.geology.rock.RockColumnState;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;

import java.util.Arrays;
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
        for (int index = 0; index < expected; index++) {
            RockColumnState state = decodeState(states[index]);
            if (state == RockColumnState.OBSERVED) {
                if (blockIds[index] < 0
                        || rockY[index] < 0
                        || rockY[index] >= worldSizeY) {
                    throw new IllegalArgumentException(
                            "observed ROCK cell requires valid blockId and Y"
                    );
                }
            } else if (blockIds[index] != -1 || rockY[index] != -1) {
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
        this.states = states.clone();
        this.blockIds = blockIds.clone();
        this.rockY = rockY.clone();
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

    byte[] stateCodes() {
        return states.clone();
    }

    int[] blockIds() {
        return blockIds.clone();
    }

    int[] rockY() {
        return rockY.clone();
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

        public int cellCount() {
            return Math.multiplyExact(width, height);
        }
    }

    @Override
    public String toString() {
        return "UpperRockTile[coordinate=" + coordinate
                + ", geometry=" + width + "x" + height
                + ", cells=" + states.length + "]";
    }
}
