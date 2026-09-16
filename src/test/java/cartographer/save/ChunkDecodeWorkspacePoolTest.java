package cartographer.save;

import cartographer.parser.ChunkDecodeWorkspace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void duplicateReleaseFailsWithoutDuplicatingQueueEntry() {
        try (ChunkDecodeWorkspacePool pool = new ChunkDecodeWorkspacePool(2)) {
            ChunkDecodeWorkspace first = pool.borrow();
            ChunkDecodeWorkspace second = pool.borrow();

            pool.release(first);
            assertThrows(IllegalStateException.class, () -> pool.release(first));
            pool.release(second);
        }
    }

    @Test
    void foreignReleaseFails() {
        try (ChunkDecodeWorkspacePool pool = new ChunkDecodeWorkspacePool(1);
             ChunkDecodeWorkspace foreign = new ChunkDecodeWorkspace()) {
            assertThrows(IllegalStateException.class, () -> pool.release(foreign));
        }
    }
}
