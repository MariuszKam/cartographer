package cartographer.parser;

import cartographer.marker.MarkerStore;
import cartographer.marker.UserMarker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsAndLoadsMarkers() {
        MarkerStore store = new MarkerStore(tempDir.resolve("markers.csv"));

        store.add(new UserMarker("Base", 10.0, 20.0));
        store.add(new UserMarker("Mine", -5.5, 30.25));

        assertEquals(2, store.load().size());
        assertEquals("Base", store.load().get(0).name());
        assertEquals(-5.5, store.load().get(1).x(), 0.001);
    }
}
