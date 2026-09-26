package cartographer.marker;

import cartographer.model.DisplayPosition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                marker(
                        "Red Clay",
                        -834.0,
                        259.0
                )
        );

        store.put(
                savePath,
                marker(
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
                marker(
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
                marker(
                        "RED CLAY",
                        -834.0,
                        259.0
                )
        );

        store.put(
                savePath,
                marker(
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
                        marker(
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
                marker(
                        "ONE",
                        1.0,
                        2.0
                )
        );

        store.put(
                savePath,
                marker(
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
    void storesPerSaveFilesUnderConfigMarkerDirectory() throws Exception {
        MarkerStore store = store();
        Path savePath = tempDir.resolve("world.vcdbs");

        store.put(
                savePath,
                marker("BASE", 10.0, 20.0)
        );

        Path markerDirectory = tempDir.resolve("markers");
        assertTrue(Files.isDirectory(markerDirectory));
        try (var files = Files.list(markerDirectory)) {
            assertEquals(1L, files.count());
        }
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
                marker(
                        "FIRST WORLD",
                        10.0,
                        20.0
                )
        );

        store.put(
                secondSave,
                marker(
                        "SECOND WORLD",
                        30.0,
                        40.0
                )
        );

        assertEquals(
                List.of(
                        marker(
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
                        marker(
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

    @Test
    void markerRejectsNonHorizontalDisplayPosition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserMarker(
                        "BASE",
                        new DisplayPosition(10.0, 1.0, 20.0)
                )
        );
    }

    private UserMarker marker(
            String name,
            double x,
            double z
    ) {
        return new UserMarker(
                name,
                new DisplayPosition(x, 0.0, z)
        );
    }

    private MarkerStore store() {
        return new MarkerStore(
                tempDir
        );
    }
}