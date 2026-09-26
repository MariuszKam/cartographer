package cartographer.navigation;

import cartographer.model.DisplayPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsNonHorizontalDisplayPosition() {
        HomeStore store = new HomeStore(tempDir.resolve("home.properties"));

        assertThrows(
                IllegalArgumentException.class,
                () -> store.save(
                        tempDir.resolve("world.vcdbs"),
                        new DisplayPosition(10.0, 1.0, 20.0)
                )
        );
    }

    @Test
    void storesDifferentHomesForDifferentSaves() {
        HomeStore store =
                new HomeStore(
                        tempDir.resolve(
                                "home.properties"
                        )
                );

        Path saveOne =
                tempDir.resolve(
                        "world-one.vcdbs"
                );

        Path saveTwo =
                tempDir.resolve(
                        "world-two.vcdbs"
                );

        store.save(
                saveOne,
                new DisplayPosition(
                        -500,
                        0.0,
                        300
                )
        );

        store.save(
                saveTwo,
                new DisplayPosition(
                        1500,
                        0.0,
                        -700
                )
        );

        assertTrue(
                store.load(saveOne).isPresent()
        );

        assertTrue(
                store.load(saveTwo).isPresent()
        );

        assertEquals(
                new DisplayPosition(
                        -500,
                        0.0,
                        300
                ),
                store.load(saveOne)
                        .orElseThrow()
        );

        assertEquals(
                new DisplayPosition(
                        1500,
                        0.0,
                        -700
                ),
                store.load(saveTwo)
                        .orElseThrow()
        );
    }
}