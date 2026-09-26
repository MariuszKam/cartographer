package cartographer.application;

import cartographer.render.RenderTileCoordinate;

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
    private final Map<RenderTileCoordinate, ScheduledTile> queued =
            new HashMap<>();
    private final Set<RenderTileCoordinate> inFlight = new HashSet<>();
    private final Set<RenderTileCoordinate> terminal = new HashSet<>();

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
        Objects.requireNonNull(coordinate, "coordinate is required");
        Objects.requireNonNull(priority, "priority is required");
        if (closed
                || terminal.contains(coordinate)
                || inFlight.contains(coordinate)) {
            return false;
        }

        ScheduledTile existing = queued.get(coordinate);
        if (existing != null) {
            if (priority.ordinal() >= existing.priority().ordinal()) {
                return false;
            }
            queue.remove(existing);
            ScheduledTile upgraded = new ScheduledTile(
                    coordinate,
                    priority,
                    existing.sequence()
            );
            queue.add(upgraded);
            queued.put(coordinate, upgraded);
            notifyAll();
            return true;
        }

        ScheduledTile candidate = new ScheduledTile(
                coordinate,
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
            queued.remove(worst.coordinate());
        }

        queue.add(candidate);
        queued.put(coordinate, candidate);
        notifyAll();
        return true;
    }

    synchronized ScheduledTile take() throws InterruptedException {
        while (queue.isEmpty() && !closed) {
            wait();
        }
        if (closed) {
            return null;
        }
        ScheduledTile next = queue.remove();
        queued.remove(next.coordinate());
        inFlight.add(next.coordinate());
        return next;
    }

    synchronized void terminal(RenderTileCoordinate coordinate) {
        inFlight.remove(coordinate);
        terminal.add(coordinate);
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
            RenderTileCoordinate coordinate,
            ProgressiveTilePriority priority,
            long sequence
    ) {
        ScheduledTile {
            Objects.requireNonNull(coordinate, "coordinate is required");
            Objects.requireNonNull(priority, "priority is required");
        }
    }
}
