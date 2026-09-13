package cartographer.ui;

import cartographer.application.SurfaceResourceMatch;
import cartographer.model.BlockInfo;
import cartographer.model.SurfaceBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceResourcePresetTest {
    @Test
    void obsidianPresetUsesPreciseSurfaceMatcher() {
        SurfaceResourcePreset preset = SurfaceResourcePreset.OBSIDIAN;
        SurfaceResourceMatch match = new SurfaceResourceMatch(
                preset.label(),
                preset.requiredTokens(),
                preset.acceptedCodePrefixes()
        );

        assertTrue(match.matches(new SurfaceBlock(
                0,
                5,
                0,
                new BlockInfo(1, "mod:looseboulders-obsidian-ice")
        )));
    }
}
