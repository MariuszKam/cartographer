package cartographer.save;

import cartographer.parser.ChunkDecodeWorkspace;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class ChunkDecodeWorkspacePool implements AutoCloseable {
    private final ArrayBlockingQueue<ChunkDecodeWorkspace> available;
    private final ChunkDecodeWorkspace[] all;
    private final AtomicBoolean[] borrowed;
    private boolean closed;

    // The pool owns every workspace from construction until close().
    @SuppressWarnings("resource")
    ChunkDecodeWorkspacePool(int size) {
        if (size <= 0) throw new IllegalArgumentException("pool size must be positive");
        available = new ArrayBlockingQueue<>(size);
        all = new ChunkDecodeWorkspace[size];
        borrowed = new AtomicBoolean[size];
        for (int i = 0; i < size; i++) {
            all[i] = createWorkspace();
            borrowed[i] = new AtomicBoolean();
            available.add(all[i]);
        }
    }

    private static ChunkDecodeWorkspace createWorkspace() {
        return new ChunkDecodeWorkspace();
    }

    ChunkDecodeWorkspace borrow() {
        if (closed) throw new IllegalStateException("workspace pool is closed");
        ChunkDecodeWorkspace workspace = available.poll();
        if (workspace == null) throw new IllegalStateException("no decode workspace available");
        int slot = slotOf(workspace);
        if (slot < 0 || !borrowed[slot].compareAndSet(false, true)) {
            throw new IllegalStateException("decode workspace ownership is corrupted");
        }
        return workspace;
    }

    void release(ChunkDecodeWorkspace workspace) {
        if (workspace == null || closed) {
            throw new IllegalStateException("decode workspace could not be returned");
        }
        int slot = slotOf(workspace);
        if (slot < 0 || !borrowed[slot].compareAndSet(true, false)) {
            throw new IllegalStateException("decode workspace is not currently borrowed");
        }
        if (!available.offer(workspace)) {
            borrowed[slot].set(true);
            throw new IllegalStateException("decode workspace could not be returned");
        }
    }

    private int slotOf(ChunkDecodeWorkspace workspace) {
        for (int slot = 0; slot < all.length; slot++) {
            if (all[slot] == workspace) return slot;
        }
        return -1;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            for (ChunkDecodeWorkspace workspace : all) workspace.close();
            available.clear();
        }
    }
}
