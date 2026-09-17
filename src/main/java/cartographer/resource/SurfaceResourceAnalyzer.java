package cartographer.resource;

import cartographer.model.SurfaceBlock;
import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceMapScanResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;

/** Internal implementation retained to preserve the existing material clustering algorithm. */
final class SurfaceResourceAnalyzer {

    public SurfaceResourceAnalysis analyze(
            SurfaceMapScanResult surface,
            List<String> requiredTokens,
            String displayName
    ) {
        String normalizedName = normalize(displayName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Surface resource display name is required");
        }
        List<BlockInfo> matchingEntries = new ArrayList<>();
        List<String> normalizedTokens = requiredTokens.stream().map(this::normalize).toList();
        for (Map.Entry<Integer, BlockInfo> entry : surface.registry().entrySet()) {
            String code = entry.getValue() == null ? "" : normalize(entry.getValue().code());
            if (!code.isBlank() && normalizedTokens.stream().allMatch(code::contains)) {
                matchingEntries.add(entry.getValue());
            }
        }
        matchingEntries.sort(java.util.Comparator.comparingInt(BlockInfo::id));
        int[] matchingIds = matchingEntries.stream().mapToInt(BlockInfo::id).toArray();
        String[] matchingCodes = matchingEntries.stream().map(BlockInfo::code).toArray(String[]::new);

        PrimitiveMatches matches = new PrimitiveMatches();
        surface.map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            int matchIndex = java.util.Arrays.binarySearch(matchingIds, blockId);
            if (matchIndex >= 0) {
                matches.add(x, y, z, matchingCodes[matchIndex]);
            }
        });
        return new SurfaceResourceAnalysis(
                normalizedName,
                surface.columnsScanned(),
                matches.points(),
                matches.deposits(normalizedName)
        );
    }

    public SurfaceResourceAnalysis analyze(
            List<SurfaceBlock> surfaceBlocks,
            String query
    ) {
        String normalizedQuery =
                normalize(
                        query
                );

        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "Surface resource query is required"
            );
        }

        List<SurfaceBlock> matchingBlocks =
                surfaceBlocks.stream()
                        .filter(block -> block.blockInfo() != null
                                && block.blockInfo().code() != null
                                && normalize(block.blockInfo().code()).contains(normalizedQuery))
                        .toList();
        return analyzeMatched(
                normalizedQuery,
                matchingBlocks,
                surfaceBlocks.size()
        );
    }

    public SurfaceResourceAnalysis analyzeMatched(
            String displayName,
            List<SurfaceBlock> matchingBlocks,
            int totalSurfaceColumns
    ) {
        String normalizedName = normalize(displayName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Surface resource display name is required"
            );
        }
        if (totalSurfaceColumns < 0) {
            throw new IllegalArgumentException(
                    "Surface resource column count must not be negative"
            );
        }

        List<SurfaceResourcePoint> matching =
                new ArrayList<>();

        Map<Long, SurfaceResourcePoint> remaining =
                new HashMap<>();

        for (SurfaceBlock block : matchingBlocks) {
            if (block.blockInfo() == null
                    || block.blockInfo().code() == null) {

                continue;
            }

            String blockCode =
                    block.blockInfo()
                            .code();

            SurfaceResourcePoint point =
                    new SurfaceResourcePoint(
                            block.worldX(),
                            block.y(),
                            block.worldZ(),
                            blockCode
                    );

            matching.add(
                    point
            );

            remaining.put(
                    coordinateKey(
                            point.worldX(),
                            point.worldZ()
                    ),
                    point
            );
        }

        List<SurfaceResourceDeposit> deposits =
                new ArrayList<>();

        while (!remaining.isEmpty()) {
            SurfaceResourcePoint start =
                    remaining.values()
                            .iterator()
                            .next();

            remaining.remove(
                    coordinateKey(
                            start.worldX(),
                            start.worldZ()
                    )
            );

            deposits.add(
                    collectDeposit(
                            normalizedName,
                            start,
                            remaining
                    )
            );
        }

        deposits.sort(
                Comparator.comparingInt(
                                SurfaceResourceDeposit::blockCount
                        )
                        .reversed()
                        .thenComparingDouble(
                                SurfaceResourceDeposit::centerWorldX
                        )
                        .thenComparingDouble(
                                SurfaceResourceDeposit::centerWorldZ
                        )
        );

        return new SurfaceResourceAnalysis(
                normalizedName,
                totalSurfaceColumns,
                List.copyOf(
                        matching
                ),
                List.copyOf(
                        deposits
                )
        );
    }

    private SurfaceResourceDeposit collectDeposit(
            String query,
            SurfaceResourcePoint start,
            Map<Long, SurfaceResourcePoint> remaining
    ) {
        ArrayDeque<SurfaceResourcePoint> queue =
                new ArrayDeque<>();

        queue.add(
                start
        );

        int blockCount =
                0;

        int minX =
                Integer.MAX_VALUE;

        int maxX =
                Integer.MIN_VALUE;

        int minZ =
                Integer.MAX_VALUE;

        int maxZ =
                Integer.MIN_VALUE;

        int minY =
                Integer.MAX_VALUE;

        int maxY =
                Integer.MIN_VALUE;

        long totalX =
                0;

        long totalZ =
                0;

        Set<String> codes =
                new TreeSet<>();

        while (!queue.isEmpty()) {
            SurfaceResourcePoint point =
                    queue.removeFirst();

            blockCount++;

            minX =
                    Math.min(
                            minX,
                            point.worldX()
                    );

            maxX =
                    Math.max(
                            maxX,
                            point.worldX()
                    );

            minZ =
                    Math.min(
                            minZ,
                            point.worldZ()
                    );

            maxZ =
                    Math.max(
                            maxZ,
                            point.worldZ()
                    );

            minY =
                    Math.min(
                            minY,
                            point.y()
                    );

            maxY =
                    Math.max(
                            maxY,
                            point.y()
                    );

            totalX +=
                    point.worldX();

            totalZ +=
                    point.worldZ();

            codes.add(
                    point.blockCode()
            );

            for (int dz = -1;
                 dz <= 1;
                 dz++) {

                for (int dx = -1;
                     dx <= 1;
                     dx++) {

                    if (dx == 0
                            && dz == 0) {

                        continue;
                    }

                    int neighborX =
                            point.worldX()
                                    + dx;

                    int neighborZ =
                            point.worldZ()
                                    + dz;

                    SurfaceResourcePoint neighbor =
                            remaining.remove(
                                    coordinateKey(
                                            neighborX,
                                            neighborZ
                                    )
                            );

                    if (neighbor != null) {
                        queue.addLast(
                                neighbor
                        );
                    }
                }
            }
        }

        return new SurfaceResourceDeposit(
                query,
                blockCount,
                codes,
                minX,
                maxX,
                minZ,
                maxZ,
                minY,
                maxY,
                totalX
                        / (double) blockCount,
                totalZ
                        / (double) blockCount
        );
    }

    private long coordinateKey(
            int x,
            int z
    ) {
        return ((long) x << 32)
                ^ (z & 0xFFFFFFFFL);
    }

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static final class PrimitiveMatches {
        private int[] xs = new int[16];
        private int[] ys = new int[16];
        private int[] zs = new int[16];
        private String[] codes = new String[16];
        private int size;
        private final PrimitiveLongIntMap indexes = new PrimitiveLongIntMap();

        private void add(int x, int y, int z, String code) {
            ensure(size + 1);
            xs[size] = x;
            ys[size] = y;
            zs[size] = z;
            codes[size] = code;
            indexes.put(coordinateKey(x, z), size);
            size++;
        }

        private List<SurfaceResourcePoint> points() {
            List<SurfaceResourcePoint> result = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                result.add(new SurfaceResourcePoint(xs[index], ys[index], zs[index], codes[index]));
            }
            return List.copyOf(result);
        }

        private List<SurfaceResourceDeposit> deposits(String query) {
            boolean[] visited = new boolean[size];
            int[] queue = new int[size];
            List<SurfaceResourceDeposit> deposits = new ArrayList<>();
            for (int start = 0; start < size; start++) {
                if (visited[start]) continue;
                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                visited[start] = true;
                int count = 0;
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                long totalX = 0, totalZ = 0;
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
                            if (dx == 0 && dz == 0) continue;
                            int neighbor = indexes.get(coordinateKey(xs[index] + dx, zs[index] + dz));
                            if (neighbor >= 0 && !visited[neighbor]) {
                                visited[neighbor] = true;
                                queue[tail++] = neighbor;
                            }
                        }
                    }
                }
                deposits.add(new SurfaceResourceDeposit(
                        query, count, blockCodes, minX, maxX, minZ, maxZ,
                        minY, maxY, totalX / (double) count, totalZ / (double) count));
            }
            deposits.sort(Comparator.comparingInt(SurfaceResourceDeposit::blockCount)
                    .reversed()
                    .thenComparingDouble(SurfaceResourceDeposit::centerWorldX)
                    .thenComparingDouble(SurfaceResourceDeposit::centerWorldZ));
            return List.copyOf(deposits);
        }

        private void ensure(int required) {
            if (required <= xs.length) return;
            int next = Math.max(required, Math.multiplyExact(xs.length, 2));
            xs = Arrays.copyOf(xs, next);
            ys = Arrays.copyOf(ys, next);
            zs = Arrays.copyOf(zs, next);
            codes = Arrays.copyOf(codes, next);
        }

        private static long coordinateKey(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }
    }

    private static final class PrimitiveLongIntMap {
        private long[] keys = new long[32];
        private int[] values = new int[32];
        private boolean[] used = new boolean[32];
        private int size;

        private void put(long key, int value) {
            if (size * 2 >= keys.length) rehash();
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
                if (keys[slot] == key) return values[slot];
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
                if (oldUsed[index]) put(oldKeys[index], oldValues[index]);
            }
        }
    }
}
