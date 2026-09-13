package cartographer.application;

import cartographer.geology.rock.RockMapMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderRockMapRequestTest {
    @Test
    void acceptsUpperRockRequestWithoutY() {
        assertDoesNotThrow(() -> new RenderRockMapRequest(
                Path.of("world.vcdbs"),
                RockMapMode.UPPER_ROCK,
                128,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty()
        ));
    }

    @Test
    void acceptsAtYRequestWithY() {
        assertDoesNotThrow(() -> new RenderRockMapRequest(
                Path.of("world.vcdbs"),
                RockMapMode.AT_Y,
                128,
                Optional.empty(),
                OptionalInt.of(60),
                OptionalInt.empty(),
                OptionalInt.empty()
        ));
    }

    @Test
    void rejectsAtYRequestWithoutY() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderRockMapRequest(
                        Path.of("world.vcdbs"),
                        RockMapMode.AT_Y,
                        128,
                        Optional.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OptionalInt.empty()
                )
        );
    }

    @Test
    void rejectsYForUpperRockRequest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderRockMapRequest(
                        Path.of("world.vcdbs"),
                        RockMapMode.UPPER_ROCK,
                        128,
                        Optional.empty(),
                        OptionalInt.of(60),
                        OptionalInt.empty(),
                        OptionalInt.empty()
                )
        );
    }
}
