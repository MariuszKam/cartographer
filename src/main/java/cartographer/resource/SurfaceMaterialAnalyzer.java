package cartographer.resource;

import cartographer.application.SurfaceMaterialMatch;
import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceMapScanResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Material-specific analysis and connected-area clustering for compact Surface data. */
public final class SurfaceMaterialAnalyzer {

    public SurfaceMaterialAnalysis analyze(
            SurfaceMapScanResult surface,
            SurfaceMaterialMatch match,
            String displayName
    ) {
        if (surface == null || match == null) {
            throw new IllegalArgumentException(
                    "Surface result and material match are required"
            );
        }
        String normalizedName = normalize(displayName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Surface resource display name is required"
            );
        }

        List<Map.Entry<Integer, BlockInfo>> matchingEntries =
                surface.registry().entrySet().stream()
                        .filter(entry -> match.matches(entry.getValue()))
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
        int[] matchingIds = matchingEntries.stream()
                .mapToInt(Map.Entry::getKey)
                .toArray();
        String[] matchingCodes = matchingEntries.stream()
                .map(entry -> entry.getValue().code())
                .toArray(String[]::new);

        PrimitiveMatches matches = new PrimitiveMatches();
        surface.map().forEachResolvedCell(
                (x, z, y, blockId, liquidId, surfaceClass) -> {
                    int matchIndex = Arrays.binarySearch(
                            matchingIds,
                            blockId
                    );
                    if (matchIndex >= 0) {
                        matches.add(
                                x,
                                y,
                                z,
                                matchingCodes[matchIndex]
                        );
                    }
                }
        );

        return new SurfaceMaterialAnalysis(
                normalizedName,
                surface.columnsScanned(),
                matches.points(),
                matches.deposits(normalizedName)
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class PrimitiveMatches {
        private int[] xs = new int[16];
        private int[] ys = new int[16];
        private int[] zs = new int[16];
        private String[] codes = new String[16];
        private int size;
        private final PrimitiveLongIntMap indexes =
                new PrimitiveLongIntMap();

        private void add(
                int x,
                int y,
                int z,
                String code
        ) {
            ensure(size + 1);
            xs[size] = x;
            ys[size] = y;
            zs[size] = z;
            codes[size] = code;
            indexes.put(coordinateKey(x, z), size);
            size++;
        }

        private List<SurfaceResourcePoint> points() {
            List<SurfaceResourcePoint> result =
                    new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                result.add(new SurfaceResourcePoint(
                        xs[index],
                        ys[index],
                        zs[index],
                        codes[index]
                ));
            }
            return List.copyOf(result);
        }

        private List<SurfaceMaterialDeposit> deposits(
                String query
        ) {
            boolean[] visited = new boolean[size];
            int[] queue = new int[size];
            List<SurfaceMaterialDeposit> deposits =
                    new ArrayList<>();

            for (int start = 0; start < size; start++) {
                if (visited[start]) {
                    continue;
                }

                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                visited[start] = true;

                int count = 0;
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxZ = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                long totalX = 0;
                long totalZ = 0;
                Set<String> blockCodes = new TreeSet<>();

                while (head < tail) {
                    int index = queue[head++];
                    count++;
                    minX = Math.min(minX, xs[index]);
                    maxX = Math.max(maxX, xs[index]);
                    minZ = Math.min(minZ, zs[index]);
                    maxZ = Math.max(maxZ, zs[index]);
                    minY = Math.min(minY, ys[index]);
                    maxY = Math.max(maxY, ys[index]);
                    totalX += xs[index];
                    totalZ += zs[index];
                    blockCodes.add(codes[index]);

                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            int neighbor = indexes.get(
                                    coordinateKey(
                                            xs[index] + dx,
                                            zs[index] + dz
                                    )
                            );
                            if (neighbor >= 0 && !visited[neighbor]) {
                                visited[neighbor] = true;
                                queue[tail++] = neighbor;
                            }
                        }
                    }
                }

                deposits.add(new SurfaceMaterialDeposit(
                        query,
                        count,
                        blockCodes,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        minY,
                        maxY,
                        totalX / (double) count,
                        totalZ / (double) count
                ));
            }

            deposits.sort(
                    Comparator.comparingInt(
                                    SurfaceMaterialDeposit::blockCount
                            )
                            .reversed()
                            .thenComparingDouble(
                                    SurfaceMaterialDeposit::centerWorldX
                            )
                            .thenComparingDouble(
                                    SurfaceMaterialDeposit::centerWorldZ
                            )
            );
            return List.copyOf(deposits);
        }

        private void ensure(int required) {
            if (required <= xs.length) {
                return;
            }
            int next = Math.max(
                    required,
                    Math.multiplyExact(xs.length, 2)
            );
            xs = Arrays.copyOf(xs, next);
            ys = Arrays.copyOf(ys, next);
            zs = Arrays.copyOf(zs, next);
            codes = Arrays.copyOf(codes, next);
        }

        private static long coordinateKey(
                int x,
                int z
        ) {
            return ((long) x << 32)
                    ^ (z & 0xFFFFFFFFL);
        }
    }

    private static final class PrimitiveLongIntMap {
        private long[] keys = new long[32];
        private int[] values = new int[32];
        private boolean[] used = new boolean[32];
        private int size;

        private void put(
                long key,
                int value
        ) {
            if (size * 2 >= keys.length) {
                rehash();
            }
            int slot = slot(key);
            if (!used[slot]) {
                used[slot] = true;
                size++;
            }
            keys[slot] = key;
            values[slot] = value;
        }

        private int get(long key) {
            int slot = slot(key);
            while (used[slot]) {
                if (keys[slot] == key) {
                    return values[slot];
                }
                slot = (slot + 1) & (keys.length - 1);
            }
            return -1;
        }

        private int slot(long key) {
            long mixed = key ^ (key >>> 33);
            mixed *= 0xff51afd7ed558ccdl;
            mixed ^= mixed >>> 33;
            return (int) mixed & (keys.length - 1);
        }

        private void rehash() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            boolean[] oldUsed = used;

            keys = new long[oldKeys.length * 2];
            values = new int[keys.length];
            used = new boolean[keys.length];
            size = 0;

            for (int index = 0; index < oldKeys.length; index++) {
                if (oldUsed[index]) {
                    put(oldKeys[index], oldValues[index]);
                }
            }
        }
    }
}
