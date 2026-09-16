package cartographer.save;

import cartographer.parser.ChunkDecodeWorkspace;

import java.util.concurrent.ArrayBlockingQueue;

final class ChunkDecodeWorkspacePool implements AutoCloseable {
    private final ArrayBlockingQueue<ChunkDecodeWorkspace> available;
    private final ChunkDecodeWorkspace[] all;
    private boolean closed;

    ChunkDecodeWorkspacePool(int size) {
        if (size <= 0) throw new IllegalArgumentException("pool size must be positive");
        available = new ArrayBlockingQueue<>(size);
        all = new ChunkDecodeWorkspace[size];
        for (int i = 0; i < size; i++) {
            all[i] = new ChunkDecodeWorkspace();
            available.add(all[i]);
        }
    }

    ChunkDecodeWorkspace borrow() {
        if (closed) throw new IllegalStateException("workspace pool is closed");
        ChunkDecodeWorkspace workspace = available.poll();
        if (workspace == null) throw new IllegalStateException("no decode workspace available");
        return workspace;
    }

    void release(ChunkDecodeWorkspace workspace) {
        if (workspace == null || closed || !owns(workspace) || !available.offer(workspace)) {
            throw new IllegalStateException("decode workspace could not be returned");
        }
    }

    private boolean owns(ChunkDecodeWorkspace workspace) {
        for (ChunkDecodeWorkspace candidate : all) {
            if (candidate == workspace) return true;
        }
        return false;
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
