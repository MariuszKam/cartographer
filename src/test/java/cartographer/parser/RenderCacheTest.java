package cartographer.parser;

import cartographer.perf.CacheKey;
import cartographer.perf.RenderCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsCacheContent() throws Exception {
        Path save = tempDir.resolve("test save.vcdbs");
        Files.writeString(save, "fixture");
        RenderCache cache = new RenderCache(tempDir.resolve("cache"));
        CacheKey key = cache.key(save);

        cache.write(key, "chunk,1,2,3");

        assertTrue(cache.exists(key));
        assertEquals("chunk,1,2,3", cache.read(key));
        assertTrue(key.fileName().startsWith("test_save.vcdbs-"));
    }
}
