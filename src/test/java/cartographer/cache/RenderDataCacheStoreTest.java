package cartographer.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cartographer.model.MapChunkCoordinate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderDataCacheStoreTest {
    private static final String SCHEMA = RenderDataCacheManifest.CURRENT_SCHEMA_VERSION;
    private static final String COMPATIBILITY = RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION;

    @Test
    void normalizedEquivalentPathsShareIdentityButDifferentDirectoriesDoNot() {
        Path base = Path.of("cache-fixture").toAbsolutePath().normalize();
        RenderDataCacheIdentity first = new RenderDataCacheIdentity(
                base.resolve("one").resolve("world.vcdbs")
        );
        RenderDataCacheIdentity equivalent = new RenderDataCacheIdentity(
                base.resolve("one").resolve(".").resolve("world.vcdbs")
        );
        RenderDataCacheIdentity differentDirectory = new RenderDataCacheIdentity(
                base.resolve("two").resolve("world.vcdbs")
        );
        RenderDataCacheIdentity suppliedUppercase = new RenderDataCacheIdentity(
                first.normalizedSavePath(),
                first.namespaceHash().toUpperCase(Locale.ROOT)
        );
        RenderDataCacheRevision firstRevision = new RenderDataCacheRevision(
                first, 4096, 1000, SCHEMA, COMPATIBILITY
        );
        RenderDataCacheRevision uppercaseRevision = new RenderDataCacheRevision(
                suppliedUppercase, 4096, 1000, SCHEMA, COMPATIBILITY
        );

        assertEquals(first, equivalent);
        assertEquals(first, suppliedUppercase);
        assertEquals(first.namespaceHash(), suppliedUppercase.namespaceHash());
        assertEquals(firstRevision.revisionHash(), uppercaseRevision.revisionHash());
        assertNotEquals(first.namespaceHash(), differentDirectory.namespaceHash());
    }

    @Test
    void manifestRoundTripAndRevisionChange(@TempDir Path cacheRoot) {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision original = revision(4096, 1000);
        RenderDataCacheRevision changed = revision(4096, 1001);

        store.publish(original);

        assertTrue(store.find(original).isPresent());
        assertTrue(store.find(changed).isEmpty());
        assertNotEquals(original.revisionHash(), changed.revisionHash());
    }

    @Test
    void saveSizeChangeUsesASeparateRevisionNamespace(@TempDir Path cacheRoot) {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision original = revision(4096, 1000);
        RenderDataCacheRevision changed = revision(4097, 1000);

        assertNotEquals(original.revisionHash(), changed.revisionHash());
        assertNotEquals(
                store.manifestPath(original).getParent(),
                store.manifestPath(changed).getParent()
        );

        store.publish(original);
        store.publish(changed);
        TerrainTileStore originalTiles = new TerrainTileStore(store, original);
        TerrainTileStore changedTiles = new TerrainTileStore(store, changed);
        originalTiles.publish(java.util.List.of(new TerrainHeightTile(
                new MapChunkCoordinate(0, 0), true, true, new int[1024]
        )));

        assertEquals(
                TerrainTileLookup.Status.HIT,
                originalTiles.read(java.util.List.of(new MapChunkCoordinate(0, 0)))
                        .get(new MapChunkCoordinate(0, 0)).status()
        );
        assertEquals(
                TerrainTileLookup.Status.MISS,
                changedTiles.read(java.util.List.of(new MapChunkCoordinate(0, 0)))
                        .get(new MapChunkCoordinate(0, 0)).status()
        );
    }

    @Test
    void schemaMismatchAndMalformedManifestAreMisses(@TempDir Path cacheRoot) throws IOException {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = revision(4096, 1000);
        Path manifest = store.manifestPath(revision);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "schemaVersion=unknown\n");

        assertTrue(store.find(revision).isEmpty());
        assertTrue(store.find(new RenderDataCacheRevision(
                revision.identity(),
                revision.saveSize(),
                revision.saveModifiedMillis(),
                "render-data-v2",
                COMPATIBILITY
        )).isEmpty());
    }

    @Test
    void temporaryManifestIsNotAHit(@TempDir Path cacheRoot) throws IOException {
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = revision(4096, 1000);
        Path manifest = store.manifestPath(revision);
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest.resolveSibling(".manifest-partial.tmp"),
                new RenderDataCacheManifest(revision).serialize());

        assertTrue(store.find(revision).isEmpty());
    }

    private static RenderDataCacheRevision revision(long size, long modifiedMillis) {
        return new RenderDataCacheRevision(
                new RenderDataCacheIdentity(Path.of("fixtures", "world.vcdbs")),
                size,
                modifiedMillis,
                SCHEMA,
                COMPATIBILITY
        );
    }
}
