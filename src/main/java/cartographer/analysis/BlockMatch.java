package cartographer.analysis;

import cartographer.model.BlockInfo;

public record BlockMatch(int worldX, int y, int worldZ, BlockInfo blockInfo) {
}
