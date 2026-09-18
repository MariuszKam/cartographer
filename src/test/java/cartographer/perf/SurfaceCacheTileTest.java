package cartographer.perf;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.SurfaceClass;
import cartographer.model.SurfaceClassCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceCacheTileTest {
    private static final String SCHEMA = RenderDataCacheManifest.CURRENT_SCHEMA_VERSION;
    private static final String COMPATIBILITY = RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION;

    @Test
    void explicitSurfaceClassCodesRoundTrip() {
        for (SurfaceClass value : SurfaceClass.values()) {
            assertEquals(value, SurfaceClassCode.decode(SurfaceClassCode.encode(value)));
        }
    }

    @Test
    void modelOwnsPrimitiveArraysAndRejectsNonCanonicalStates() {
        byte[] state = resolvedState();
        int[] heights = filled(42);
        int[] blocks = filled(7);
        int[] liquids = filled(8);
        byte[] classes = filledBytes(SurfaceClassCode.encode(SurfaceClass.GRASS));
        SurfaceCacheTile tile = new SurfaceCacheTile(new MapChunkCoordinate(1, 2), 2, 2,
                state, heights, blocks, liquids, classes,
                SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST, 4, 0, 0);
        state[0] = 0;
        heights[0] = 0;
        assertEquals(SurfaceCacheTile.RESOLVED, tile.state()[0]);
        assertEquals(42, tile.surfaceY()[0]);

        assertThrows(IllegalArgumentException.class, () -> new SurfaceCacheTile(
                new MapChunkCoordinate(1, 2), 1, 1,
                new byte[]{1}, new int[]{0}, new int[]{0}, new int[]{0},
                new byte[]{SurfaceClassCode.encode(SurfaceClass.UNKNOWN)},
                SurfaceCacheTile.SourceMode.FALLBACK, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceCacheTile(
                new MapChunkCoordinate(1, 2), 1, 1,
                new byte[]{SurfaceCacheTile.RESOLVED}, new int[]{1}, new int[]{1}, new int[]{1},
                new byte[]{SurfaceClassCode.encode(SurfaceClass.GRASS)},
                SurfaceCacheTile.SourceMode.FALLBACK, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceCacheTile(
                new MapChunkCoordinate(1, 2), 1, 1,
                new byte[]{SurfaceCacheTile.LIQUID_UNAVAILABLE}, new int[]{0}, new int[]{0}, new int[]{0},
                new byte[]{SurfaceClassCode.encode(SurfaceClass.UNKNOWN)},
                SurfaceCacheTile.SourceMode.FALLBACK, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceCacheTile(
                new MapChunkCoordinate(1, 2), 1, 1,
                new byte[]{0}, new int[]{1}, new int[]{0}, new int[]{0},
                new byte[]{SurfaceClassCode.encode(SurfaceClass.UNKNOWN)},
                SurfaceCacheTile.SourceMode.FALLBACK, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurfaceCacheTile(
                new MapChunkCoordinate(1, 2), 1, 1,
                new byte[]{(byte) 0x7F}, new int[]{0}, new int[]{0}, new int[]{0},
                new byte[]{SurfaceClassCode.encode(SurfaceClass.UNKNOWN)},
                SurfaceCacheTile.SourceMode.FALLBACK, 1, 0, 0));
    }

    @Test
    void codecRoundTripAndMalformedPayloadsAreRejected() {
        SurfaceCacheTile tile = resolvedTile(new MapChunkCoordinate(7, 9),
                SurfaceCacheTile.SourceMode.FALLBACK, 4, 1, 2);
        byte[] encoded = SurfaceCacheTileCodec.encode(tile);
        SurfaceCacheTile decoded = SurfaceCacheTileCodec.decode(encoded);
        assertEquals(tile.coordinate(), decoded.coordinate());
        assertEquals(tile.sourceMode(), decoded.sourceMode());
        assertEquals(4, decoded.diagnosticColumnsScanned());
        assertArrayEquals(tile.state(), decoded.state());
        assertArrayEquals(tile.surfaceY(), decoded.surfaceY());

        byte[] wrongMagic = encoded.clone();
        wrongMagic[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> SurfaceCacheTileCodec.decode(wrongMagic));
        byte[] wrongVersion = encoded.clone();
        ByteBuffer.wrap(wrongVersion).order(ByteOrder.BIG_ENDIAN).putInt(4, 99);
        assertThrows(IllegalArgumentException.class, () -> SurfaceCacheTileCodec.decode(wrongVersion));
        byte[] wrongProfile = encoded.clone();
        ByteBuffer.wrap(wrongProfile).order(ByteOrder.BIG_ENDIAN).putInt(8, 99);
        assertThrows(IllegalArgumentException.class, () -> SurfaceCacheTileCodec.decode(wrongProfile));
        byte[] invalidDimensions = encoded.clone();
        ByteBuffer.wrap(invalidDimensions).order(ByteOrder.BIG_ENDIAN).putInt(20, 0);
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceCacheTileCodec.decode(invalidDimensions));
        byte[] invalidCount = encoded.clone();
        ByteBuffer.wrap(invalidCount).order(ByteOrder.BIG_ENDIAN).putInt(44, 3);
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceCacheTileCodec.decode(invalidCount));
        byte[] invalidClass = encoded.clone();
        invalidClass[invalidClass.length - 1] = 99;
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceCacheTileCodec.decode(invalidClass));
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceCacheTileCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceCacheTileCodec.decode(concat(encoded, new byte[]{1})));
    }

    @Test
    void storeBatchesRoundTripMissingCorruptAndRevisionIsolation(@TempDir Path cacheRoot) throws Exception {
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision first = revision(1);
        RenderDataCacheRevision second = revision(2);
        cacheStore.publish(first);
        cacheStore.publish(second);
        SurfaceTileStore firstStore = new SurfaceTileStore(cacheStore, first);
        SurfaceTileStore secondStore = new SurfaceTileStore(cacheStore, second);
        MapChunkCoordinate coordinate = new MapChunkCoordinate(3, 4);
        SurfaceCacheTile tile = resolvedTile(coordinate, SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST, 4, 0, 0);

        firstStore.publish(List.of(tile));
        Map<MapChunkCoordinate, SurfaceTileLookup> result = firstStore.read(
                List.of(coordinate, new MapChunkCoordinate(8, 9), coordinate));
        assertEquals(SurfaceTileLookup.Status.HIT, result.get(coordinate).status());
        assertEquals(SurfaceTileLookup.Status.MISS,
                result.get(new MapChunkCoordinate(8, 9)).status());
        assertEquals(List.of(coordinate, new MapChunkCoordinate(8, 9)), List.copyOf(result.keySet()));
        assertEquals(SurfaceTileLookup.Status.MISS,
                secondStore.read(List.of(coordinate)).get(coordinate).status());

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + firstStore.databasePath().toAbsolutePath().normalize().toUri());
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE surface_tile SET payload = ? WHERE mapchunk_x = ? AND mapchunk_z = ?")) {
            statement.setBytes(1, new byte[]{1, 2, 3});
            statement.setInt(2, coordinate.x());
            statement.setInt(3, coordinate.z());
            statement.executeUpdate();
        }
        assertEquals(SurfaceTileLookup.Status.CORRUPT,
                firstStore.read(List.of(coordinate)).get(coordinate).status());
        firstStore.publish(List.of(tile));
        assertEquals(SurfaceTileLookup.Status.HIT,
                firstStore.read(List.of(coordinate)).get(coordinate).status());
    }

    private static SurfaceCacheTile resolvedTile(MapChunkCoordinate coordinate,
                                                  SurfaceCacheTile.SourceMode mode,
                                                  int scanned, int empty, int unavailable) {
        return new SurfaceCacheTile(coordinate, 2, 2, resolvedState(), filled(42), filled(7),
                filled(8), filledBytes(SurfaceClassCode.encode(SurfaceClass.GRASS)),
                mode, scanned, empty, unavailable);
    }

    private static byte[] resolvedState() {
        return new byte[]{SurfaceCacheTile.CONSIDERED | SurfaceCacheTile.RESOLVED,
                SurfaceCacheTile.CONSIDERED | SurfaceCacheTile.RESOLVED,
                SurfaceCacheTile.CONSIDERED | SurfaceCacheTile.RESOLVED,
                SurfaceCacheTile.CONSIDERED | SurfaceCacheTile.RESOLVED};
    }

    private static int[] filled(int value) {
        int[] values = new int[4];
        Arrays.fill(values, value);
        return values;
    }

    private static byte[] filledBytes(byte value) {
        byte[] values = new byte[4];
        Arrays.fill(values, value);
        return values;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static RenderDataCacheRevision revision(long modifiedMillis) {
        return new RenderDataCacheRevision(
                new RenderDataCacheIdentity(Path.of("fixtures", "world.vcdbs")),
                1024, modifiedMillis, SCHEMA, COMPATIBILITY);
    }
}
