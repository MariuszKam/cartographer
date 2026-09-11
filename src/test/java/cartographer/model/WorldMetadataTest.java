package cartographer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldMetadataTest {

    @Test
    void convertsAbsoluteSavePositionToDisplayPosition() {
        WorldMetadata metadata =
                new WorldMetadata(
                        1_024_000,
                        256,
                        1_024_000
                );

        WorldPosition absolute =
                new WorldPosition(
                        511398.381,
                        108.0,
                        512290.782
                );

        DisplayPosition display =
                metadata.toDisplay(
                        absolute
                );

        assertEquals(
                -601.619,
                display.x(),
                0.001
        );

        assertEquals(
                108.0,
                display.y(),
                0.001
        );

        assertEquals(
                290.782,
                display.z(),
                0.001
        );
    }
}