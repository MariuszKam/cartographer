package cartographer.navigation;

import cartographer.model.HomeLocation;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionCalculatorTest {
    @Test
    void calculatesNorthWestDirectionToHome() {
        Direction direction = new DirectionCalculator()
                .fromPlayerToHome(new WorldPosition(512341, 112, 511782), new HomeLocation(512100, 511900));

        assertEquals("NW", direction.compass());
        assertEquals(268, Math.round(direction.distanceBlocks()));
    }
}
