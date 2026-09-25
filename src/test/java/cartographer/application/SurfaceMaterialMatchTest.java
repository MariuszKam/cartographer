package cartographer.application;

import cartographer.resource.SurfaceMaterialMatch;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.model.WorldMetadata;
import cartographer.resource.SurfaceMaterialAnalysis;
import cartographer.resource.SurfaceMaterialAnalyzer;
import cartographer.resource.SurfaceResourcePoint;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceTileAccumulator;
import cartographer.scanner.SurfaceTileLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceMaterialMatchTest {

    @Test
    void fireClayRequiresBothTokens() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("fire", "clay")
        );

        assertTrue(match.matches(block("game:fire-clay-blue")));
        assertFalse(match.matches(block("game:firepit")));
        assertFalse(match.matches(block("game:clay-blue")));
    }

    @Test
    void matchingIsCaseInsensitive() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("FiRe", "ClAy")
        );

        assertTrue(match.matches(block("GAME:FIRE-CLAY-BLUE")));
    }

    @Test
    void customSubstringUsesOneToken() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "redclay", List.of("redclay")
        );

        assertTrue(match.matches(block("game:redclay")));
        assertFalse(match.matches(block("game:clay")));
    }

    @Test
    void clayAndPeatMatchTheirMaterialTokens() {
        assertTrue(new SurfaceMaterialMatch("Clay", List.of("clay"))
                .matches(block("game:clay-blue")));
        assertTrue(new SurfaceMaterialMatch("Peat", List.of("peat"))
                .matches(block("game:peat-normal")));
        assertFalse(new SurfaceMaterialMatch("Peat", List.of("peat"))
                .matches(block("game:clay-blue")));
    }

    @Test
    void supportedMaterialResolverRejectsObjectAndUnknownNames() {
        assertTrue(SurfaceMaterialPreset.resolve("Fire Clay").isPresent());
        assertTrue(SurfaceMaterialPreset.resolve("clay").isPresent());
        assertTrue(SurfaceMaterialPreset.resolve("PEAT").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("obsidian").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("nativecopper").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("cassiterite").isPresent());
        assertFalse(SurfaceMaterialPreset.resolve("arbitrary-unknown-value").isPresent());
    }

    @Test
    void analyzerKeepsExactPointsAndGroupsAdjacentMaterialBlocks() {
        SurfaceMaterialMatch match = new SurfaceMaterialMatch(
                "Fire Clay", List.of("fire", "clay")
        );
        SurfaceMapScanResult surface = surface(
                Map.of(
                        1, new BlockInfo(1, "game:fire-clay-blue"),
                        2, new BlockInfo(2, "game:fire-clay-blue")
                ),
                new Cell(10, 5, 20, 1),
                new Cell(11, 5, 20, 2)
        );

        SurfaceMaterialAnalysis result = new SurfaceMaterialAnalyzer().analyze(
                surface,
                match,
                match.displayName()
        );

        assertEquals(2, result.matchedBlockCount());
        assertEquals(1, result.depositCount());
        assertEquals(List.of(10, 11), result.matchingBlocks().stream()
                .map(SurfaceResourcePoint::worldX).toList());
    }

    @Test
    void emptyMatchInputProducesValidAnalysis() {
        SurfaceMaterialMatch match =
                new SurfaceMaterialMatch("peat", List.of("peat"));
        SurfaceMaterialAnalysis analysis =
                new SurfaceMaterialAnalyzer().analyze(
                        surface(Map.of()),
                        match,
                        match.displayName()
                );

        assertTrue(analysis.matchingBlocks().isEmpty());
        assertTrue(analysis.deposits().isEmpty());
    }

    private SurfaceMapScanResult surface(
            Map<Integer, BlockInfo> registry,
            Cell... cells
    ) {
        SurfaceTileAccumulator accumulator =
                new SurfaceTileAccumulator(
                        SurfaceTileLayout.forSurface(
                                16,
                                16,
                                16,
                                new WorldMetadata(64, 256, 64)
                        )
                );
        for (Cell cell : cells) {
            accumulator.recordSurface(
                    cell.worldX(),
                    cell.worldZ(),
                    cell.worldY(),
                    cell.blockId(),
                    0,
                    SurfaceClass.UNKNOWN
            );
        }
        return new SurfaceMapScanResult(
                accumulator.finish(),
                registry,
                0,
                cells.length,
                0,
                0
        );
    }

    private BlockInfo block(String code) {
        return new BlockInfo(1, code);
    }

    private record Cell(
            int worldX,
            int worldY,
            int worldZ,
            int blockId
    ) {
    }
}
