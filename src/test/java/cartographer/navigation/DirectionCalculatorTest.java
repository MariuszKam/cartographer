package cartographer.navigation;

import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionCalculatorTest {

    private final DirectionCalculator calculator =
            new DirectionCalculator();

    private final DisplayPosition player =
            new DisplayPosition(
                    0.0,
                    100.0,
                    0.0
            );

    @Test
    void calculatesCardinalDirections() {
        assertDirection(
                "N",
                0,
                -100
        );

        assertDirection(
                "E",
                100,
                0
        );

        assertDirection(
                "S",
                0,
                100
        );

        assertDirection(
                "W",
                -100,
                0
        );
    }

    @Test
    void calculatesDiagonalDirections() {
        assertDirection(
                "NE",
                100,
                -100
        );

        assertDirection(
                "SE",
                100,
                100
        );

        assertDirection(
                "SW",
                -100,
                100
        );

        assertDirection(
                "NW",
                -100,
                -100
        );
    }

    @Test
    void calculatesExpectedBearings() {
        Direction east =
                calculator.fromPlayerToHome(
                        player,
                        new HomeLocation(
                                100,
                                0
                        )
                );

        Direction south =
                calculator.fromPlayerToHome(
                        player,
                        new HomeLocation(
                                0,
                                100
                        )
                );

        assertEquals(
                90.0,
                east.bearingDegrees(),
                0.001
        );

        assertEquals(
                180.0,
                south.bearingDegrees(),
                0.001
        );
    }

    private void assertDirection(
            String expected,
            double homeX,
            double homeZ
    ) {
        Direction direction =
                calculator.fromPlayerToHome(
                        player,
                        new HomeLocation(
                                homeX,
                                homeZ
                        )
                );

        assertEquals(
                expected,
                direction.compass()
        );
    }
}