package cartographer.resource;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurfaceObjectSelectionAnalysisTest {
    @Test
    void aggregatesResourcesWithoutMergingTheirIdentity() {
        SurfaceObjectAnalysis copper = analysis("Native Copper", "game:nativecopper",
                SurfaceObjectFamily.ORE_BITS, 3);
        SurfaceObjectAnalysis obsidian = analysis("Obsidian", "game:obsidian",
                SurfaceObjectFamily.LOOSE_STONE, 5);

        SurfaceObjectSelectionAnalysis aggregate = new SurfaceObjectSelectionAnalysis(
                List.of(obsidian, copper));

        assertEquals(2, aggregate.resourceCount());
        assertEquals(8, aggregate.occurrenceCount());
        assertEquals(List.of("game:nativecopper", "game:obsidian"),
                aggregate.resources().stream().map(SurfaceObjectAnalysis::qualifiedResourceKey).toList());
    }

    private SurfaceObjectAnalysis analysis(String name, String key,
                                           SurfaceObjectFamily family, int count) {
        return new SurfaceObjectAnalysis(name, key, 1,
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> new SurfaceResourcePoint(index, 60, index,
                                "game:loose-object"))
                        .toList(), new TreeSet<>(Set.of(family)));
    }

    @Test
    void rejectsDuplicateLogicalResources() {
        SurfaceObjectAnalysis first = analysis("One", "game:one",
                SurfaceObjectFamily.FLINT, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new SurfaceObjectSelectionAnalysis(List.of(first, first)));
    }
}
