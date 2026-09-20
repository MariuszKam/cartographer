package cartographer.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationVersionTest {

    @Test
    void parsesStableSemanticVersions() {
        assertEquals(
                new ApplicationVersion(1, 2, 3),
                ApplicationVersion.parse("1.2.3")
        );
        assertEquals(
                new ApplicationVersion(0, 0, 0),
                ApplicationVersion.parse("0.0.0")
        );
    }

    @Test
    void comparesNumericVersionComponents() {
        assertTrue(
                ApplicationVersion.parse("1.0.1")
                        .compareTo(ApplicationVersion.parse("1.0.0")) > 0
        );
        assertTrue(
                ApplicationVersion.parse("1.10.0")
                        .compareTo(ApplicationVersion.parse("1.9.9")) > 0
        );
        assertTrue(
                ApplicationVersion.parse("2.0.0")
                        .compareTo(ApplicationVersion.parse("1.99.99")) > 0
        );
    }

    @Test
    void rejectsUnsupportedOrMalformedVersions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationVersion.parse("1.2")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationVersion.parse("01.2.3")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationVersion.parse("1.2.3-beta.1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationVersion.parse("v1.2.3")
        );
    }

    @Test
    void generatedRuntimeVersionIsPresentAndValid() {
        ApplicationVersion current = ApplicationVersion.current();

        assertEquals(
                current,
                ApplicationVersion.parse(current.toString())
        );
    }
}
