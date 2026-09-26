package cartographer.snapshot;

import cartographer.environment.ClimateSummary;
import cartographer.environment.EnvironmentLabel;
import cartographer.environment.EnvironmentProfile;
import cartographer.environment.ForestDensityClass;
import cartographer.environment.ForestSummary;
import cartographer.environment.IdMapSummary;
import cartographer.environment.OceanSummary;
import cartographer.geology.GeologicProvinceSummary;
import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic compact binary codec for interpreted mapregion snapshot state. */
final class MapRegionSnapshotEntryCodec {
    private static final int MAGIC = 0x4D523234; // MR24
    private static final int VERSION = 2;
    private static final int PROFILE_VERSION = 1;
    private static final int MAX_LIST_VALUES = 1_024;
    private static final int MAX_RESOURCE_MAPS = 4_096;
    private static final int MAX_RESOURCE_MAP_VALUES = 1_048_576;

    private MapRegionSnapshotEntryCodec() {
    }

    static byte[] encode(MapRegionSnapshotEntry entry) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(PROFILE_VERSION);
                out.writeInt(entry.coordinate().x());
                out.writeInt(entry.coordinate().z());
                out.writeInt(labelMask(entry.environmentProfile().labels()));

                writeOptionalClimate(out, entry.environmentProfile().climate());
                writeOptionalForest(out, entry.environmentProfile().forest());
                writeOptionalOcean(out, entry.environmentProfile().ocean());
                writeOptionalIdSummary(out, entry.environmentProfile().landform());
                writeOptionalIdSummary(out, entry.environmentProfile().geologicProvince());
                writeOptionalGeology(out, entry.geologySummary());
                writeOreMaps(out, entry.oreMaps());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "cannot encode mapregion snapshot entry",
                    exception
            );
        }
    }

    static MapRegionSnapshotEntry decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException(
                    "mapregion snapshot payload is empty"
            );
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(encoded)
        )) {
            require(in.readInt() == MAGIC, "wrong mapregion snapshot magic");
            require(in.readInt() == VERSION, "unsupported mapregion snapshot version");
            require(
                    in.readInt() == PROFILE_VERSION,
                    "unsupported mapregion interpretation profile"
            );
            MapRegionCoordinate coordinate = new MapRegionCoordinate(
                    in.readInt(),
                    in.readInt()
            );
            Set<EnvironmentLabel> labels = decodeLabels(in.readInt());
            EnvironmentProfile profile = new EnvironmentProfile(
                    coordinate,
                    readOptionalClimate(in),
                    readOptionalForest(in),
                    readOptionalOcean(in),
                    readOptionalIdSummary(in),
                    readOptionalIdSummary(in),
                    labels
            );
            Optional<GeologicProvinceSummary> geology =
                    readOptionalGeology(in, coordinate);
            Map<String, IntDataMap2D> oreMaps = readOreMaps(in);
            require(
                    in.available() == 0,
                    "mapregion snapshot payload has trailing bytes"
            );
            return new MapRegionSnapshotEntry(
                    coordinate,
                    profile,
                    geology,
                    oreMaps
            );
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "mapregion snapshot payload is truncated",
                    exception
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "cannot decode mapregion snapshot payload",
                    exception
            );
        }
    }

    private static void writeOptionalClimate(
            DataOutputStream out,
            Optional<ClimateSummary> value
    ) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        ClimateSummary summary = value.orElseThrow();
        out.writeInt(summary.samples());
        out.writeDouble(summary.averageTemperatureIndex());
        out.writeDouble(summary.averageRainfallIndex());
        writeIntList(out, summary.rawSample());
    }

    private static Optional<ClimateSummary> readOptionalClimate(
            DataInputStream in
    ) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        return Optional.of(new ClimateSummary(
                nonNegative(in.readInt(), "climate samples"),
                in.readDouble(),
                in.readDouble(),
                readIntList(in)
        ));
    }

    private static void writeOptionalForest(
            DataOutputStream out,
            Optional<ForestSummary> value
    ) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        ForestSummary summary = value.orElseThrow();
        out.writeInt(summary.samples());
        out.writeInt(summary.rawMin());
        out.writeInt(summary.rawMax());
        out.writeDouble(summary.averageNormalizedDensity());
        out.writeInt(summary.averageDensityClass().ordinal());
    }

    private static Optional<ForestSummary> readOptionalForest(
            DataInputStream in
    ) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        int samples = nonNegative(in.readInt(), "forest samples");
        int rawMin = in.readInt();
        int rawMax = in.readInt();
        double average = in.readDouble();
        int ordinal = in.readInt();
        ForestDensityClass[] values = ForestDensityClass.values();
        require(
                ordinal >= 0 && ordinal < values.length,
                "invalid forest density class"
        );
        return Optional.of(new ForestSummary(
                samples,
                rawMin,
                rawMax,
                average,
                values[ordinal]
        ));
    }

    private static void writeOptionalOcean(
            DataOutputStream out,
            Optional<OceanSummary> value
    ) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        OceanSummary summary = value.orElseThrow();
        out.writeInt(summary.samples());
        out.writeInt(summary.rawMin());
        out.writeInt(summary.rawMax());
        out.writeDouble(summary.averageRawValue());
    }

    private static Optional<OceanSummary> readOptionalOcean(
            DataInputStream in
    ) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        return Optional.of(new OceanSummary(
                nonNegative(in.readInt(), "ocean samples"),
                in.readInt(),
                in.readInt(),
                in.readDouble()
        ));
    }

    private static void writeOptionalIdSummary(
            DataOutputStream out,
            Optional<IdMapSummary> value
    ) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        IdMapSummary summary = value.orElseThrow();
        out.writeInt(summary.samples());
        out.writeInt(summary.distinctCount());
        writeIntList(out, summary.dominantIds());
    }

    private static Optional<IdMapSummary> readOptionalIdSummary(
            DataInputStream in
    ) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        return Optional.of(new IdMapSummary(
                nonNegative(in.readInt(), "id-map samples"),
                nonNegative(in.readInt(), "id-map distinct count"),
                readIntList(in)
        ));
    }

    private static void writeOptionalGeology(
            DataOutputStream out,
            Optional<GeologicProvinceSummary> value
    ) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        GeologicProvinceSummary summary = value.orElseThrow();
        out.writeInt(summary.samples());
        out.writeInt(summary.distinctCount());
        writeIntList(out, summary.dominantIds());
    }

    private static Optional<GeologicProvinceSummary> readOptionalGeology(
            DataInputStream in,
            MapRegionCoordinate coordinate
    ) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        return Optional.of(new GeologicProvinceSummary(
                coordinate,
                nonNegative(in.readInt(), "geology samples"),
                nonNegative(in.readInt(), "geology distinct count"),
                readIntList(in)
        ));
    }

    private static void writeOreMaps(
            DataOutputStream out,
            Map<String, IntDataMap2D> oreMaps
    ) throws IOException {
        List<Map.Entry<String, IntDataMap2D>> entries =
                oreMaps.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
        if (entries.size() > MAX_RESOURCE_MAPS) {
            throw new IllegalArgumentException(
                    "too many resource maps in mapregion snapshot"
            );
        }
        out.writeInt(entries.size());
        for (Map.Entry<String, IntDataMap2D> entry : entries) {
            out.writeUTF(entry.getKey());
            IntDataMap2D map = entry.getValue();
            int[] values = map.data();
            if (values.length > MAX_RESOURCE_MAP_VALUES) {
                throw new IllegalArgumentException(
                        "resource map is unexpectedly large"
                );
            }
            out.writeInt(map.size());
            out.writeInt(map.topLeftPadding());
            out.writeInt(map.bottomRightPadding());
            out.writeInt(values.length);
            for (int value : values) {
                out.writeInt(value);
            }
        }
    }

    private static Map<String, IntDataMap2D> readOreMaps(
            DataInputStream in
    ) throws IOException {
        int count = in.readInt();
        require(
                count >= 0 && count <= MAX_RESOURCE_MAPS,
                "invalid resource map count"
        );
        LinkedHashMap<String, IntDataMap2D> result =
                new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = in.readUTF();
            require(!key.isBlank(), "blank resource map key");
            int size = in.readInt();
            int topLeftPadding = in.readInt();
            int bottomRightPadding = in.readInt();
            int length = in.readInt();
            require(size > 0, "invalid resource map size");
            long expected = (long) size * size;
            require(
                    expected == length
                            && length >= 0
                            && length <= MAX_RESOURCE_MAP_VALUES,
                    "invalid resource map payload length"
            );
            int[] values = new int[length];
            for (int valueIndex = 0;
                 valueIndex < length;
                 valueIndex++) {
                values[valueIndex] = in.readInt();
            }
            IntDataMap2D previous = result.put(
                    key,
                    new IntDataMap2D(
                            size,
                            topLeftPadding,
                            bottomRightPadding,
                            values
                    )
            );
            require(
                    previous == null,
                    "duplicate resource map key"
            );
        }
        return Map.copyOf(result);
    }

    private static void writeIntList(
            DataOutputStream out,
            List<Integer> values
    ) throws IOException {
        List<Integer> safe = List.copyOf(values);
        if (safe.size() > MAX_LIST_VALUES) {
            throw new IllegalArgumentException(
                    "mapregion snapshot list is unexpectedly large"
            );
        }
        out.writeInt(safe.size());
        for (Integer value : safe) {
            out.writeInt(value);
        }
    }

    private static List<Integer> readIntList(DataInputStream in)
            throws IOException {
        int count = in.readInt();
        require(
                count >= 0 && count <= MAX_LIST_VALUES,
                "invalid mapregion snapshot list length"
        );
        List<Integer> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(in.readInt());
        }
        return List.copyOf(values);
    }

    private static int labelMask(Set<EnvironmentLabel> labels) {
        int mask = 0;
        for (EnvironmentLabel label : labels) {
            mask |= 1 << label.ordinal();
        }
        return mask;
    }

    private static Set<EnvironmentLabel> decodeLabels(int mask) {
        EnumSet<EnvironmentLabel> result =
                EnumSet.noneOf(EnvironmentLabel.class);
        int knownMask = 0;
        for (EnvironmentLabel label : EnvironmentLabel.values()) {
            int bit = 1 << label.ordinal();
            knownMask |= bit;
            if ((mask & bit) != 0) {
                result.add(label);
            }
        }
        require(
                (mask & ~knownMask) == 0,
                "unknown environment label bits"
        );
        return Set.copyOf(result);
    }

    private static int nonNegative(int value, String name) {
        require(value >= 0, name + " cannot be negative");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
