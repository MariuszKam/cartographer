package cartographer.save;

import cartographer.parser.ChunkDecodeWorkspace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;

class ChunkDecodeWorkspacePoolTest {
    @Test
    void boundedPoolReturnsDistinctWorkspacesAndAllowsReuse() {
        try (ChunkDecodeWorkspacePool pool = new ChunkDecodeWorkspacePool(2)) {
            ChunkDecodeWorkspace first = pool.borrow();
            ChunkDecodeWorkspace second = pool.borrow();
            assertNotSame(first, second);

            pool.release(first);
            pool.release(second);

            ChunkDecodeWorkspace reused = pool.borrow();
            pool.release(reused);
        }
    }
}
