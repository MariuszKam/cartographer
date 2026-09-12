package cartographer.marker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putReplacesMarkerWithSameNameCaseInsensitively() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        store.put(
                savePath,
                new UserMarker(
                        "Red Clay",
                        -834.0,
                        259.0
                )
        );

        store.put(
                savePath,
                new UserMarker(
                        "RED CLAY",
                        -800.0,
                        300.0
                )
        );

        List<UserMarker> markers =
                store.load(
                        savePath
                );

        assertEquals(
                1,
                markers.size()
        );

        assertEquals(
                new UserMarker(
                        "RED CLAY",
                        -800.0,
                        300.0
                ),
                markers.getFirst()
        );
    }

    @Test
    void removeDeletesOnlyRequestedMarker() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        store.put(
                savePath,
                new UserMarker(
                        "RED CLAY",
                        -834.0,
                        259.0
                )
        );

        store.put(
                savePath,
                new UserMarker(
                        "BLUE CLAY",
                        -579.0,
                        337.0
                )
        );

        boolean removed =
                store.remove(
                        savePath,
                        "red clay"
                );

        assertTrue(
                removed
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "BLUE CLAY",
                                -579.0,
                                337.0
                        )
                ),
                store.load(
                        savePath
                )
        );
    }

    @Test
    void clearDeletesAllMarkersForSave() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        store.put(
                savePath,
                new UserMarker(
                        "ONE",
                        1.0,
                        2.0
                )
        );

        store.put(
                savePath,
                new UserMarker(
                        "TWO",
                        3.0,
                        4.0
                )
        );

        int removed =
                store.clear(
                        savePath
                );

        assertEquals(
                2,
                removed
        );

        assertTrue(
                store.load(
                                savePath
                        )
                        .isEmpty()
        );
    }

    @Test
    void markersAreIsolatedBetweenDifferentSavePaths() {
        MarkerStore store =
                store();

        Path firstSave =
                tempDir.resolve(
                                "first"
                        )
                        .resolve(
                                "world.vcdbs"
                        );

        Path secondSave =
                tempDir.resolve(
                                "second"
                        )
                        .resolve(
                                "world.vcdbs"
                        );

        store.put(
                firstSave,
                new UserMarker(
                        "FIRST WORLD",
                        10.0,
                        20.0
                )
        );

        store.put(
                secondSave,
                new UserMarker(
                        "SECOND WORLD",
                        30.0,
                        40.0
                )
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "FIRST WORLD",
                                10.0,
                                20.0
                        )
                ),
                store.load(
                        firstSave
                )
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "SECOND WORLD",
                                30.0,
                                40.0
                        )
                ),
                store.load(
                        secondSave
                )
        );
    }

    private MarkerStore store() {
        return new MarkerStore(
                tempDir.resolve(
                        "markers.csv"
                )
        );
    }
}