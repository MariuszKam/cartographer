package cartographer.perf.workload;

/** Pure geometry helpers for circular world-column workloads. */
public final class WorkloadGeometry {
    private WorkloadGeometry() {
    }

    public static double approximateCircularArea(RadiusProfile radius) {
        if (radius == null) {
            throw new NullPointerException("radius is required");
        }
        return Math.PI * radius.blocks() * radius.blocks();
    }

    public static long exactLatticeColumnCount(RadiusProfile radius) {
        if (radius == null) {
            throw new NullPointerException("radius is required");
        }
        return exactLatticeColumnCount(radius.blocks());
    }

    private static long exactLatticeColumnCount(int radius) {
        long squaredRadius = (long) radius * radius;
        long count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                long distanceSquared = (long) x * x + (long) z * z;
                if (distanceSquared <= squaredRadius) {
                    count++;
                }
            }
        }
        return count;
    }
}
