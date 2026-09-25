package cartographer.parser;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.ParseResult;
import cartographer.binary.ProtobufWireReader;

import java.util.List;

public class MapChunkParser {
    private static final int RAIN_HEIGHT_MAP_FIELD = 3;
    private static final int WORLD_GEN_TERRAIN_HEIGHT_MAP_FIELD = 7;
    private static final int HEIGHT_MAP_VALUES = 32 * 32;

    public ParseResult<MapChunk> parse(MapChunkCoordinate coordinate, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return ParseResult.failure("mapchunk payload is empty");
        }

        try {
            List<Long> rainHeightMap =
                    ProtobufWireReader.readRepeatedUInt32Field(
                            payload,
                            RAIN_HEIGHT_MAP_FIELD
                    );

            List<Long> worldGenTerrainHeightMap =
                    ProtobufWireReader.readRepeatedUInt32Field(
                            payload,
                            WORLD_GEN_TERRAIN_HEIGHT_MAP_FIELD
                    );

            if (rainHeightMap.isEmpty()
                    && worldGenTerrainHeightMap.isEmpty()) {
                return ParseResult.failure(
                        "mapchunk has no RainHeightMap or WorldGenTerrainHeightMap"
                );
            }

            int[] rain =
                    validateHeightMap(
                            "RainHeightMap",
                            rainHeightMap
                    );

            int[] worldGen =
                    validateHeightMap(
                            "WorldGenTerrainHeightMap",
                            worldGenTerrainHeightMap
                    );

            return ParseResult.success(
                    new MapChunk(
                            coordinate,
                            rain,
                            worldGen
                    )
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ParseResult.failure(
                    "invalid ServerMapChunk protobuf: "
                            + exception.getMessage()
            );
        }
    }

    private int[] validateHeightMap(
            String name,
            List<Long> values
    ) {
        if (values.isEmpty()) {
            return new int[0];
        }

        if (values.size() != HEIGHT_MAP_VALUES) {
            throw new IllegalArgumentException(
                    name
                            + " must contain "
                            + HEIGHT_MAP_VALUES
                            + " values, got "
                            + values.size()
            );
        }

        int[] heights =
                new int[HEIGHT_MAP_VALUES];

        for (int index = 0; index < values.size(); index++) {
            long value =
                    values.get(index);

            if (value < 0 || value > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException(
                        name
                                + " value at index "
                                + index
                                + " is outside uint32 range"
                );
            }

            heights[index] =
                    (int) value;
        }

        return heights;
    }
}
