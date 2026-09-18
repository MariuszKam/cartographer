package cartographer.perf;

import cartographer.model.MapChunk;
import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class TerrainTileStoreTest {
    private static final String SCHEMA = RenderDataCacheManifest.CURRENT_SCHEMA_VERSION;
    private static final String COMPATIBILITY = RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION;

    @Test
    void mapChunkConversionUsesRainThenWorldgenAndDoesNotInventHeights() {
        int[] rain = filled(11);
        int[] worldgen = filled(22);
        MapChunkCoordinate coordinate = new MapChunkCoordinate(3, -4);

        TerrainHeightTile rainTile = TerrainHeightTile.from(
                new MapChunk(coordinate, rain, worldgen)
        );
        TerrainHeightTile worldgenTile = TerrainHeightTile.from(
                new MapChunk(coordinate, new int[0], worldgen)
        );
        TerrainHeightTile emptyTile = TerrainHeightTile.from(
                new MapChunk(coordinate, new int[0], new int[0])
        );

        assertTrue(rainTile.rainHeightAvailable());
        assertArrayEquals(rain, rainTile.effectiveHeights());
        assertFalse(worldgenTile.rainHeightAvailable());
        assertArrayEquals(worldgen, worldgenTile.effectiveHeights());
        assertFalse(emptyTile.effectiveHeightAvailable());
        assertEquals(0, emptyTile.effectiveHeights().length);
    }

    @Test
    void codecRoundTripAndMalformedPayloadsAreRejected() {
        TerrainHeightTile tile = new TerrainHeightTile(
                new MapChunkCoordinate(7, 9), true, true, filled(42)
        );
        byte[] encoded = TerrainHeightTileCodec.encode(tile);

        TerrainHeightTile decoded = TerrainHeightTileCodec.decode(encoded);
        assertEquals(tile.coordinate(), decoded.coordinate());
        assertTrue(decoded.rainHeightAvailable());
        assertArrayEquals(tile.effectiveHeights(), decoded.effectiveHeights());

        byte[] wrongMagic = encoded.clone();
        wrongMagic[0] = 0;
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightTileCodec.decode(wrongMagic));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightTileCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainHeightTileCodec.decode(concat(encoded, new byte[]{1})));
    }

    @Test
    void storePublishesAndReadsBatchesAndMissingCoordinates(@TempDir Path cacheRoot) {
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = revision(1);
        cacheStore.publish(revision);
        TerrainTileStore tileStore = new TerrainTileStore(cacheStore, revision);
        TerrainHeightTile tile = new TerrainHeightTile(
                new MapChunkCoordinate(1, 2), false, true, filled(5)
        );

        tileStore.publish(List.of(tile));
        Map<MapChunkCoordinate, TerrainTileLookup> result = tileStore.read(
                List.of(tile.coordinate(), new MapChunkCoordinate(8, 9))
        );

        assertEquals(TerrainTileLookup.Status.HIT, result.get(tile.coordinate()).status());
        assertArrayEquals(tile.effectiveHeights(), result.get(tile.coordinate()).tile().effectiveHeights());
        assertEquals(
                TerrainTileLookup.Status.MISS,
                result.get(new MapChunkCoordinate(8, 9)).status()
        );
        assertTrue(tileStore.databasePath().startsWith(cacheRoot.toAbsolutePath().normalize()));
    }

    @Test
    void duplicateRequestedCoordinatesCollapseInFirstOccurrenceOrder(@TempDir Path cacheRoot) {
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = revision(5);
        cacheStore.publish(revision);
        TerrainTileStore tileStore = new TerrainTileStore(cacheStore, revision);
        MapChunkCoordinate first = new MapChunkCoordinate(1, 2);
        MapChunkCoordinate second = new MapChunkCoordinate(3, 4);

        Map<MapChunkCoordinate, TerrainTileLookup> result = tileStore.read(
                List.of(first, second, first, second, first)
        );

        assertEquals(List.of(first, second), List.copyOf(result.keySet()));
    }

    @Test
    void corruptPayloadIsReportedWithoutFabricatedData(@TempDir Path cacheRoot) throws Exception {
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = revision(2);
        cacheStore.publish(revision);
        TerrainTileStore tileStore = new TerrainTileStore(cacheStore, revision);
        MapChunkCoordinate coordinate = new MapChunkCoordinate(4, 5);
        tileStore.publish(List.of(new TerrainHeightTile(coordinate, false, true, filled(8))));

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + tileStore.databasePath().toAbsolutePath().normalize().toUri()
        ); PreparedStatement statement = connection.prepareStatement(
                "UPDATE terrain_tile SET payload = ? WHERE mapchunk_x = ? AND mapchunk_z = ?"
        )) {
            statement.setBytes(1, new byte[]{0, 1, 2});
            statement.setInt(2, coordinate.x());
            statement.setInt(3, coordinate.z());
            statement.executeUpdate();
        }

        assertEquals(
                TerrainTileLookup.Status.CORRUPT,
                tileStore.read(List.of(coordinate)).get(coordinate).status()
        );

        tileStore.publish(List.of(new TerrainHeightTile(coordinate, true, true, filled(9))));
        assertEquals(
                TerrainTileLookup.Status.HIT,
                tileStore.read(List.of(coordinate)).get(coordinate).status()
        );
    }

    @Test
    void differentRevisionNamespacesCannotReadPriorTiles(@TempDir Path cacheRoot) {
        RenderDataCacheStore cacheStore = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision first = revision(3);
        RenderDataCacheRevision second = revision(4);
        cacheStore.publish(first);
        cacheStore.publish(second);
        TerrainHeightTileStorePair stores = new TerrainHeightTileStorePair(
                new TerrainTileStore(cacheStore, first),
                new TerrainTileStore(cacheStore, second)
        );
        MapChunkCoordinate coordinate = new MapChunkCoordinate(2, 3);
        stores.first.publish(List.of(new TerrainHeightTile(coordinate, true, true, filled(12))));

        assertEquals(
                TerrainTileLookup.Status.HIT,
                stores.first.read(List.of(coordinate)).get(coordinate).status()
        );
        assertEquals(
                TerrainTileLookup.Status.MISS,
                stores.second.read(List.of(coordinate)).get(coordinate).status()
        );
    }

    private static int[] filled(int value) {
        int[] values = new int[MapChunk.HEIGHT_VALUE_COUNT];
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
                1024,
                modifiedMillis,
                SCHEMA,
                COMPATIBILITY
        );
    }

    private record TerrainHeightTileStorePair(
            TerrainTileStore first,
            TerrainTileStore second
    ) {
    }
}
