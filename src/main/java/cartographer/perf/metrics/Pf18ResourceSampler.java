package cartographer.perf.metrics;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** Low-overhead, operation-scoped resource sampler with explicit unavailable values. */
public final class Pf18ResourceSampler {
    public <T> Measured<T> measure(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation is required");
        Raw before = captureBefore();
        T result = operation.get();
        Raw after = captureAfter();
        return new Measured<>(result, evidence(before, after));
    }

    private Raw captureBefore() {
        OptionalLong cpu = processCpu();
        OptionalLong gcCount = gcCount();
        OptionalLong gcTime = gcTime();
        boolean heap = resetHeapPeaks();
        return new Raw(cpu, gcCount, gcTime, heap);
    }

    private Raw captureAfter() {
        return new Raw(processCpu(), gcCount(), gcTime(), heapPeakAvailable());
    }

    private Pf18ResourceEvidence evidence(Raw before, Raw after) {
        return new Pf18ResourceEvidence(
                delta(before.cpu(), after.cpu()),
                before.heapAvailable() && after.heapAvailable() ? heapPeak() : OptionalLong.empty(),
                delta(before.gcCount(), after.gcCount()),
                delta(before.gcTime(), after.gcTime()),
                OptionalLong.empty(),
                OptionalLong.empty(),
                before.cpu().isPresent() && after.cpu().isPresent()
                        ? "OperatingSystemMXBean#getProcessCpuTime" : "UNAVAILABLE",
                before.heapAvailable() && after.heapAvailable()
                        ? "heap MemoryPoolMXBean peak usage" : "UNAVAILABLE",
                before.gcCount().isPresent() && after.gcCount().isPresent()
                        ? "GarbageCollectorMXBean delta" : "UNAVAILABLE",
                "UNAVAILABLE; whole-process allocation is not collected here",
                "UNAVAILABLE; portable RSS is not collected here");
    }

    private OptionalLong processCpu() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystem) {
            long value = operatingSystem.getProcessCpuTime();
            return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
        }
        return OptionalLong.empty();
    }

    private OptionalLong gcCount() {
        return gcValue(true);
    }

    private OptionalLong gcTime() {
        return gcValue(false);
    }

    private OptionalLong gcValue(boolean count) {
        long total = 0;
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        if (beans.isEmpty()) return OptionalLong.empty();
        for (GarbageCollectorMXBean bean : beans) {
            long value = count ? bean.getCollectionCount() : bean.getCollectionTime();
            if (value < 0) return OptionalLong.empty();
            try {
                total = Math.addExact(total, value);
            } catch (ArithmeticException overflow) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.of(total);
    }

    private boolean resetHeapPeaks() {
        boolean available = false;
        for (MemoryPoolMXBean pool : heapPools()) {
            if (pool.isUsageThresholdSupported() || pool.getType() == MemoryType.HEAP) {
                try {
                    pool.resetPeakUsage();
                    available = true;
                } catch (UnsupportedOperationException ignored) {
                    return false;
                }
            }
        }
        return available;
    }

    private boolean heapPeakAvailable() {
        return !heapPools().isEmpty();
    }

    private OptionalLong heapPeak() {
        long total = 0;
        boolean available = false;
        for (MemoryPoolMXBean pool : heapPools()) {
            if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null) {
                long used = pool.getPeakUsage().getUsed();
                if (used >= 0) {
                    total += used;
                    available = true;
                }
            }
        }
        return available ? OptionalLong.of(total) : OptionalLong.empty();
    }

    private List<MemoryPoolMXBean> heapPools() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .toList();
    }

    private static OptionalLong delta(OptionalLong before, OptionalLong after) {
        if (before.isEmpty() || after.isEmpty() || after.getAsLong() < before.getAsLong()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(after.getAsLong() - before.getAsLong());
    }

    public record Measured<T>(T result, Pf18ResourceEvidence evidence) {
        public Measured {
            Objects.requireNonNull(result);
            Objects.requireNonNull(evidence);
        }
    }

    private record Raw(OptionalLong cpu, OptionalLong gcCount, OptionalLong gcTime,
                       boolean heapAvailable) {
    }
}
