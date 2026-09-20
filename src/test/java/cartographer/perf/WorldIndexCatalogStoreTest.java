package cartographer.perf;

import cartographer.model.MapChunkCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIndexCatalogStoreTest {
    private static final String SCHEMA =
            RenderDataCacheManifest.CURRENT_SCHEMA_VERSION;
    private static final String COMPATIBILITY =
            RenderDataCacheManifest.CURRENT_COMPATIBILITY_VERSION;

    @Test
    void partialRowsAreReusableButCompletionIsExplicit(@TempDir Path root) {
        RenderDataCacheStore cache = new RenderDataCacheStore(root);
        RenderDataCacheRevision revision = revision(1L);
        cache.publish(revision);
        WorldIndexCatalogStore store =
                new WorldIndexCatalogStore(cache, revision);

        store.recordObserved(List.of(
                new MapChunkCoordinate(3, 2),
                new MapChunkCoordinate(1, 1),
                new MapChunkCoordinate(3, 2)
        ));

        assertFalse(store.mapChunkScanComplete());
        assertEquals(
                List.of(
                        new MapChunkCoordinate(1, 1),
                        new MapChunkCoordinate(3, 2)
                ),
                store.observedMapChunks()
        );

        store.markMapChunkScanComplete();

        assertTrue(store.mapChunkScanComplete());
        assertEquals(
                java.util.Set.of(new MapChunkCoordinate(3, 2)),
                store.observedAmong(List.of(
                        new MapChunkCoordinate(3, 2),
                        new MapChunkCoordinate(9, 9)
                ))
        );
    }

    @Test
    void differentRevisionsCannotReuseObservedCatalog(@TempDir Path root) {
        RenderDataCacheStore cache = new RenderDataCacheStore(root);
        RenderDataCacheRevision first = revision(1L);
        RenderDataCacheRevision second = revision(2L);
        cache.publish(first);
        cache.publish(second);

        WorldIndexCatalogStore firstStore =
                new WorldIndexCatalogStore(cache, first);
        WorldIndexCatalogStore secondStore =
                new WorldIndexCatalogStore(cache, second);
        firstStore.recordObserved(List.of(new MapChunkCoordinate(4, 5)));
        firstStore.markMapChunkScanComplete();

        assertTrue(firstStore.mapChunkScanComplete());
        assertFalse(secondStore.mapChunkScanComplete());
        assertTrue(secondStore.observedMapChunks().isEmpty());
    }

    private static RenderDataCacheRevision revision(long modifiedMillis) {
        return new RenderDataCacheRevision(
                new RenderDataCacheIdentity(
                        Path.of("fixtures", "world.vcdbs")
                ),
                1024L,
                modifiedMillis,
                SCHEMA,
                COMPATIBILITY
        );
    }
}
