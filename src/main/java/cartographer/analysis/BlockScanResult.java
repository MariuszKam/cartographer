package cartographer.analysis;

import java.util.List;

public record BlockScanResult(int chunksScanned, int blocksScanned, List<BlockMatch> matches, boolean truncated) {
}
