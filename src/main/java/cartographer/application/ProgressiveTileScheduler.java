package cartographer.application;

import cartographer.render.RenderTileCoordinate;
import cartographer.render.RenderTileKey;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

final class ProgressiveTileScheduler {
    private static final Comparator<ScheduledTile> ORDER =
            Comparator.comparingInt(
                            (ScheduledTile tile) -> tile.priority().ordinal()
                    )
                    .thenComparingLong(ScheduledTile::sequence);

    private final int capacity;
    private final PriorityQueue<ScheduledTile> queue =
            new PriorityQueue<>(ORDER);
    private final Map<RenderTileKey, ScheduledTile> queued =
            new HashMap<>();
    private final Set<RenderTileKey> inFlight = new HashSet<>();
    private final Set<RenderTileKey> terminal = new HashSet<>();

    private long nextSequence;
    private boolean closed;

    ProgressiveTileScheduler(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean offer(
            RenderTileCoordinate coordinate,
            ProgressiveTilePriority priority
    ) {
        return offer(
                RenderTileKey.fullDetail(coordinate),
                priority
        );
    }

    synchronized boolean offer(
            RenderTileKey key,
            ProgressiveTilePriority priority
    ) {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(priority, "priority is required");
        if (closed
                || terminal.contains(key)
                || inFlight.contains(key)) {
            return false;
        }

        ScheduledTile existing = queued.get(key);
        if (existing != null) {
            if (priority.ordinal() >= existing.priority().ordinal()) {
                return false;
            }
            queue.remove(existing);
            ScheduledTile upgraded = new ScheduledTile(
                    key,
                    priority,
                    existing.sequence()
            );
            queue.add(upgraded);
            queued.put(key, upgraded);
            notifyAll();
            return true;
        }

        ScheduledTile candidate = new ScheduledTile(
                key,
                priority,
                nextSequence++
        );
        if (queue.size() >= capacity) {
            ScheduledTile worst = queue.stream()
                    .max(ORDER)
                    .orElseThrow();
            if (ORDER.compare(candidate, worst) >= 0) {
                return false;
            }
            queue.remove(worst);
            queued.remove(worst.key());
        }

        queue.add(candidate);
        queued.put(key, candidate);
        notifyAll();
        return true;
    }

    synchronized ScheduledTile poll() {
        if (closed || queue.isEmpty()) {
            return null;
        }
        ScheduledTile next = queue.remove();
        queued.remove(next.key());
        inFlight.add(next.key());
        return next;
    }

    synchronized ScheduledTile take() throws InterruptedException {
        while (queue.isEmpty() && !closed) {
            wait();
        }
        if (closed) {
            return null;
        }
        ScheduledTile next = queue.remove();
        queued.remove(next.key());
        inFlight.add(next.key());
        return next;
    }

    synchronized void terminal(RenderTileKey key) {
        Objects.requireNonNull(key, "key is required");
        inFlight.remove(key);
        terminal.add(key);
        notifyAll();
    }

    synchronized void close() {
        closed = true;
        queue.clear();
        queued.clear();
        notifyAll();
    }

    synchronized int queuedCount() {
        return queue.size();
    }

    synchronized int inFlightCount() {
        return inFlight.size();
    }

    record ScheduledTile(
            RenderTileKey key,
            ProgressiveTilePriority priority,
            long sequence
    ) {
        ScheduledTile {
            Objects.requireNonNull(key, "key is required");
            Objects.requireNonNull(priority, "priority is required");
        }

        RenderTileCoordinate coordinate() {
            return key.coordinate();
        }
    }
}
