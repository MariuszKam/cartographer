package cartographer.parser;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ServerMapRegion;
import cartographer.save.ProtobufWireReader;

import java.util.Optional;

public class ServerMapRegionParser {
    /*
     * Field numbers come from the generated Vintage Story 1.21.0-rc.3
     * protobuf schema linked by the VCDBS format documentation.
     */
    public static final int LANDFORM_MAP_FIELD =
            1;

    public static final int FOREST_MAP_FIELD =
            2;

    public static final int CLIMATE_MAP_FIELD =
            3;

    public static final int GEOLOGIC_PROVINCE_MAP_FIELD =
            4;

    public static final int OCEAN_MAP_FIELD =
            18;

    private final IntDataMap2DParser intDataMapParser =
            new IntDataMap2DParser();

    public ParseResult<ServerMapRegion> parse(
            MapRegionCoordinate coordinate,
            byte[] payload
    ) {
        if (payload == null
                || payload.length == 0) {

            return ParseResult.failure(
                    "mapregion payload is empty"
            );
        }

        try {
            return ParseResult.success(
                    new ServerMapRegion(
                            coordinate,
                            parseOptionalMap(
                                    payload,
                                    CLIMATE_MAP_FIELD,
                                    "ClimateMap"
                            ),
                            parseOptionalMap(
                                    payload,
                                    FOREST_MAP_FIELD,
                                    "ForestMap"
                            ),
                            parseOptionalMap(
                                    payload,
                                    LANDFORM_MAP_FIELD,
                                    "LandformMap"
                            ),
                            parseOptionalMap(
                                    payload,
                                    GEOLOGIC_PROVINCE_MAP_FIELD,
                                    "GeologicProvinceMap"
                            ),
                            parseOptionalMap(
                                    payload,
                                    OCEAN_MAP_FIELD,
                                    "OceanMap"
                            )
                    )
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ParseResult.failure(
                    "invalid ServerMapRegion protobuf: "
                            + exception.getMessage()
            );
        }
    }

    private Optional<IntDataMap2D> parseOptionalMap(
            byte[] payload,
            int fieldNumber,
            String name
    ) {
        Optional<byte[]> mapPayload =
                ProtobufWireReader.readLengthDelimitedField(
                        payload,
                        fieldNumber
                );

        if (mapPayload.isEmpty()) {
            return Optional.empty();
        }

        ParseResult<IntDataMap2D> parsed =
                intDataMapParser.parse(
                        mapPayload.get()
                );

        if (!parsed.isSuccess()) {
            throw new IllegalArgumentException(
                    name
                            + ": "
                            + parsed.error()
                            .orElse("unable to parse IntDataMap2D")
            );
        }

        return parsed.value();
    }
}
