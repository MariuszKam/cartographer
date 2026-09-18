package cartographer.application;

import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProspectingAreaRequestTest {

    @Test
    void multiResourceSelectionIsTrimmedDeduplicatedAndOrdered() {
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of("world.vcdbs"),
                Optional.of(new WorldPosition(10, 0, 20)),
                128,
                List.of(" copper ", "tin", "copper")
        );

        assertEquals(List.of("copper", "tin"), request.resources());
        assertFalse(request.allResources());
        assertTrue(request.resource().isEmpty());
    }

    @Test
    void emptyResourceListMeansAllResources() {
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of("world.vcdbs"),
                Optional.empty(),
                128,
                List.of()
        );

        assertTrue(request.allResources());
        assertTrue(request.resource().isEmpty());
    }

    @Test
    void compatibilitySingleResourceConstructorStillWorks() {
        ProspectingAreaRequest request = new ProspectingAreaRequest(
                Path.of("world.vcdbs"),
                Optional.empty(),
                128,
                Optional.of("tin")
        );

        assertEquals(List.of("tin"), request.resources());
        assertEquals(Optional.of("tin"), request.resource());
    }

    @Test
    void rejectsBlankResourceEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProspectingAreaRequest(
                        Path.of("world.vcdbs"),
                        Optional.empty(),
                        128,
                        List.of("copper", " ")
                )
        );
    }
}
