package cartographer.parser;

import cartographer.perf.CacheKey;
import cartographer.perf.IncrementalRenderIndex;
import cartographer.perf.IncrementalState;
import cartographer.save.SaveIndex;
import cartographer.save.TableIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalRenderIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsChangedTableFingerprint() {
        IncrementalRenderIndex index = new IncrementalRenderIndex(tempDir);
        CacheKey key = new CacheKey("save.vcdbs", 1, 2, "parser");
        IncrementalState previous = index.from(new SaveIndex(List.of(new TableIndex("chunk", 1, 10L, 20L))), key);
        IncrementalState current = index.from(new SaveIndex(List.of(new TableIndex("chunk", 2, 10L, 21L))), key);

        assertEquals("changed", index.changes(previous, current).get("chunk"));
    }

    @Test
    void writesAndReadsState() {
        IncrementalRenderIndex index = new IncrementalRenderIndex(tempDir);
        CacheKey key = new CacheKey("save.vcdbs", 1, 2, "parser");
        IncrementalState state = index.from(new SaveIndex(List.of(new TableIndex("mapchunk", 4, 1L, 9L))), key);

        index.write(key, state);

        assertTrue(index.exists(key));
        assertEquals(state.tableFingerprints(), index.read(key).tableFingerprints());
    }
}
