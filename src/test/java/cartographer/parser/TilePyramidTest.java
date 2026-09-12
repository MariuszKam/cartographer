package cartographer.parser;

import cartographer.atlas.AtlasTile;
import cartographer.atlas.TilePyramid;
import cartographer.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TilePyramidTest {
    @Test
    void createsPyramidTileCountsAcrossLevels() {
        List<AtlasTile> tiles = new TilePyramid().plan(new WorldPosition(100, 0, 100), 64, 3);

        assertEquals(21, tiles.size());
        assertEquals(0, tiles.getFirst().level());
        assertEquals(64, tiles.getFirst().radiusBlocks());
        assertEquals(2, tiles.getLast().level());
        assertEquals(16, tiles.getLast().radiusBlocks());
    }
}
