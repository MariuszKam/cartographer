package cartographer.resource;

import cartographer.model.BlockInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceObjectAnalyzerTest {
    @Test
    void preservesOccurrencesAtDifferentYInTheSameColumn() {
        BlockInfo block = new BlockInfo(7, "game:loosestones-obsidian-free");
        SurfaceObjectCandidate candidate = new SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(7, block)).findByBlockId(7).orElseThrow();
        ObservedSurfaceResource resource = new ObservedSurfaceResource(candidate, List.of(
                new SurfaceObjectObservation(candidate, 10, 60, 20, 7),
                new SurfaceObjectObservation(candidate, 10, 61, 20, 7)
        ));

        SurfaceObjectAnalysis analysis = new SurfaceObjectAnalyzer().analyze(
                resource, Map.of(7, block));

        assertEquals(2, analysis.occurrenceCount());
        assertEquals(Set.of(SurfaceObjectFamily.LOOSE_STONE), analysis.families());
        assertEquals(List.of(60, 61), analysis.occurrences().stream()
                .map(SurfaceResourcePoint::y).toList());
    }

    @Test
    void preservesModdedNamespaceInAnalysisIdentityAndDisplayName() {
        BlockInfo block = new BlockInfo(7, "somemod:loosestones-obsidian-free");
        SurfaceObjectCandidate candidate = new SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(7, block)).findByBlockId(7).orElseThrow();
        ObservedSurfaceResource resource = new ObservedSurfaceResource(candidate, List.of(
                new SurfaceObjectObservation(candidate, 10, 60, 20, 7)));

        SurfaceObjectAnalysis analysis = new SurfaceObjectAnalyzer().analyze(
                resource, Map.of(7, block));

        assertEquals("Obsidian [somemod]", analysis.displayName());
        assertEquals("somemod:obsidian", analysis.qualifiedResourceKey());
        assertEquals(Set.of(SurfaceObjectFamily.LOOSE_STONE), analysis.families());
    }

    @Test
    void preservesAllFamiliesForOneLogicalResource() {
        BlockInfo stone = new BlockInfo(7, "game:loosestones-obsidian-free");
        BlockInfo flint = new BlockInfo(8, "game:looseflints-obsidian-free");
        var candidate = new SurfaceObjectCandidateCatalogBuilder()
                .build(Map.of(7, stone, 8, flint)).findByBlockId(7).orElseThrow();
        ObservedSurfaceResource resource = new ObservedSurfaceResource(candidate, List.of(
                new SurfaceObjectObservation(candidate, 10, 60, 20, 7),
                new SurfaceObjectObservation(candidate, 11, 60, 20, 8)));

        SurfaceObjectAnalysis analysis = new SurfaceObjectAnalyzer().analyze(
                resource, Map.of(7, stone, 8, flint));

        assertEquals(Set.of(SurfaceObjectFamily.FLINT, SurfaceObjectFamily.LOOSE_STONE),
                analysis.families());
    }
}
