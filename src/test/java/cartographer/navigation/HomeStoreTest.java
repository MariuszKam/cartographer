package cartographer.navigation;

import cartographer.model.HomeLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeStoreTest {

    @TempDir
    Path tempDir;

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
                new HomeLocation(
                        -500,
                        300
                )
        );

        store.save(
                saveTwo,
                new HomeLocation(
                        1500,
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
                new HomeLocation(
                        -500,
                        300
                ),
                store.load(saveOne)
                        .orElseThrow()
        );

        assertEquals(
                new HomeLocation(
                        1500,
                        -700
                ),
                store.load(saveTwo)
                        .orElseThrow()
        );
    }
}