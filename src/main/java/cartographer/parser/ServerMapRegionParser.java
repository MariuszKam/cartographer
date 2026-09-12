package cartographer.parser;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ParseResult;
import cartographer.model.ServerMapRegion;
import cartographer.save.ProtobufWireReader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public static final int ORE_MAPS_FIELD =
            8;

    public static final int ROCK_STRATA_FIELD =
            12;

    public static final int OCEAN_MAP_FIELD =
            18;

    private static final int MAP_ENTRY_KEY_FIELD =
            1;

    private static final int MAP_ENTRY_VALUE_FIELD =
            2;

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
                            ),
                            parseOreMaps(
                                    payload
                            ),
                            parseRockStrata(
                                    payload
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

    private Map<String, IntDataMap2D> parseOreMaps(
            byte[] payload
    ) {
        List<byte[]> entries =
                ProtobufWireReader.readLengthDelimitedFields(
                        payload,
                        ORE_MAPS_FIELD
                );

        if (entries.isEmpty()) {
            return Map.of();
        }

        Map<String, IntDataMap2D> maps =
                new LinkedHashMap<>();

        for (int index = 0; index < entries.size(); index++) {
            byte[] entry =
                    entries.get(
                            index
                    );

            String key =
                    parseOreMapKey(
                            entry,
                            index
                    );

            IntDataMap2D map =
                    parseRequiredMapEntryValue(
                            entry,
                            index,
                            key
                    );

            maps.put(
                    key,
                    map
            );
        }

        return Map.copyOf(
                maps
        );
    }

    private String parseOreMapKey(
            byte[] entry,
            int index
    ) {
        Optional<byte[]> keyPayload =
                ProtobufWireReader.readLengthDelimitedField(
                        entry,
                        MAP_ENTRY_KEY_FIELD
                );

        if (keyPayload.isEmpty()) {
            throw new IllegalArgumentException(
                    "OreMaps["
                            + index
                            + "] is missing key"
            );
        }

        String key =
                new String(
                        keyPayload.get(),
                        StandardCharsets.UTF_8
                );

        if (key.isBlank()) {
            throw new IllegalArgumentException(
                    "OreMaps["
                            + index
                            + "] has blank key"
            );
        }

        return key;
    }

    private IntDataMap2D parseRequiredMapEntryValue(
            byte[] entry,
            int index,
            String key
    ) {
        Optional<byte[]> valuePayload =
                ProtobufWireReader.readLengthDelimitedField(
                        entry,
                        MAP_ENTRY_VALUE_FIELD
                );

        if (valuePayload.isEmpty()) {
            throw new IllegalArgumentException(
                    "OreMaps["
                            + index
                            + "] "
                            + key
                            + " is missing IntDataMap2D value"
            );
        }

        ParseResult<IntDataMap2D> parsed =
                intDataMapParser.parse(
                        valuePayload.get()
                );

        if (!parsed.isSuccess()) {
            throw new IllegalArgumentException(
                    "OreMaps["
                            + index
                            + "] "
                            + key
                            + ": "
                            + parsed.error()
                            .orElse("unable to parse IntDataMap2D")
            );
        }

        return parsed.value()
                .orElseThrow();
    }

    private List<IntDataMap2D> parseRockStrata(
            byte[] payload
    ) {
        List<byte[]> entries =
                ProtobufWireReader.readLengthDelimitedFields(
                        payload,
                        ROCK_STRATA_FIELD
                );

        if (entries.isEmpty()) {
            return List.of();
        }

        List<IntDataMap2D> strata =
                new ArrayList<>();

        for (int index = 0; index < entries.size(); index++) {
            ParseResult<IntDataMap2D> parsed =
                    intDataMapParser.parse(
                            entries.get(
                                    index
                            )
                    );

            if (!parsed.isSuccess()) {
                throw new IllegalArgumentException(
                        "RockStrata["
                                + index
                                + "]: "
                                + parsed.error()
                                .orElse("unable to parse IntDataMap2D")
                );
            }

            strata.add(
                    parsed.value()
                            .orElseThrow()
            );
        }

        return List.copyOf(
                strata
        );
    }
}
