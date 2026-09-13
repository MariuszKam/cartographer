package cartographer.geology;

import cartographer.model.IntDataMap2D;
import cartographer.model.MapRegionCoordinate;
import cartographer.model.ServerMapRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RockStrataAnalyzerTest {

    @Test
    void summarizesInnerRawValuesWithoutTreatingThemAsRockIds() {
        IntDataMap2D map = new IntDataMap2D(
                4,
                1,
                1,
                new int[]{
                        99, 99, 99, 99,
                        99, 12, 12, 99,
                        99, 7, 12, 99,
                        99, 99, 99, 99
                }
        );
        ServerMapRegion region = new ServerMapRegion(
                new MapRegionCoordinate(0, 0),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of(map)
        );

        RockStratumSummary summary =
                new RockStrataAnalyzer()
                        .summarize(region)
                        .strata()
                        .getFirst();

        assertEquals(4, summary.samples());
        assertEquals(7, summary.minRawValue());
        assertEquals(12, summary.maxRawValue());
        assertEquals(2, summary.distinctCount());
        assertEquals(List.of(12, 7), summary.dominantRawValues());
    }
}
