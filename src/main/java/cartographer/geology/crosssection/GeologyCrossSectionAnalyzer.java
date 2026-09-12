package cartographer.geology.crosssection;

import cartographer.model.BlockInfo;
import cartographer.model.ChunkCoordinate;
import cartographer.model.ParsedChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GeologyCrossSectionAnalyzer {

    public GeologyCrossSection analyze(
            List<ParsedChunk> chunks,
            Map<Integer, BlockInfo> blockRegistry,
            int startWorldX,
            int startWorldZ,
            int endWorldX,
            int endWorldZ
    ) {
        Objects.requireNonNull(
                chunks,
                "chunks are required"
        );

        Objects.requireNonNull(
                blockRegistry,
                "blockRegistry is required"
        );

        List<HorizontalPoint> points =
                rasterize(
                        startWorldX,
                        startWorldZ,
                        endWorldX,
                        endWorldZ
                );

        Map<ChunkCoordinate, ParsedChunk> chunkIndex =
                indexChunks(
                        chunks
                );

        Set<HorizontalChunkKey> relevantHorizontalChunks =
                relevantHorizontalChunks(
                        points
                );

        VerticalBounds bounds =
                verticalBounds(
                        chunks,
                        relevantHorizontalChunks
                );

        List<GeologySectionColumn> columns =
                new ArrayList<>(
                        points.size()
                );

        for (int index = 0;
             index < points.size();
             index++) {

            HorizontalPoint point =
                    points.get(
                            index
                    );

            columns.add(
                    new GeologySectionColumn(
                            index,
                            point.worldX(),
                            point.worldZ(),
                            runsForColumn(
                                    chunkIndex,
                                    blockRegistry,
                                    point.worldX(),
                                    point.worldZ(),
                                    bounds
                            )
                    )
            );
        }

        return new GeologyCrossSection(
                startWorldX,
                startWorldZ,
                endWorldX,
                endWorldZ,
                bounds.minYInclusive(),
                bounds.maxYExclusive(),
                columns
        );
    }

    private Map<ChunkCoordinate, ParsedChunk> indexChunks(
            List<ParsedChunk> chunks
    ) {
        Map<ChunkCoordinate, ParsedChunk> indexed =
                new HashMap<>();

        for (ParsedChunk chunk : chunks) {
            Objects.requireNonNull(
                    chunk,
                    "chunks must not contain null"
            );

            ParsedChunk previous =
                    indexed.put(
                            chunk.coordinate(),
                            chunk
                    );

            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate chunk coordinate: "
                                + chunk.coordinate()
                );
            }
        }

        return indexed;
    }

    private Set<HorizontalChunkKey> relevantHorizontalChunks(
            List<HorizontalPoint> points
    ) {
        Set<HorizontalChunkKey> keys =
                new HashSet<>();

        for (HorizontalPoint point : points) {
            keys.add(
                    new HorizontalChunkKey(
                            Math.floorDiv(
                                    point.worldX(),
                                    ChunkCoordinate.SIZE_BLOCKS
                            ),
                            Math.floorDiv(
                                    point.worldZ(),
                                    ChunkCoordinate.SIZE_BLOCKS
                            )
                    )
            );
        }

        return keys;
    }

    private VerticalBounds verticalBounds(
            List<ParsedChunk> chunks,
            Set<HorizontalChunkKey> relevantHorizontalChunks
    ) {
        int minY =
                Integer.MAX_VALUE;

        int maxY =
                Integer.MIN_VALUE;

        for (ParsedChunk chunk : chunks) {
            HorizontalChunkKey key =
                    new HorizontalChunkKey(
                            chunk.coordinate().x(),
                            chunk.coordinate().z()
                    );

            if (!relevantHorizontalChunks.contains(
                    key
            )) {
                continue;
            }

            minY =
                    Math.min(
                            minY,
                            chunk.minY()
                    );

            maxY =
                    Math.max(
                            maxY,
                            Math.addExact(
                                    chunk.minY(),
                                    chunk.sizeY()
                            )
                    );
        }

        if (minY == Integer.MAX_VALUE) {
            return new VerticalBounds(
                    0,
                    0
            );
        }

        return new VerticalBounds(
                minY,
                maxY
        );
    }

    private List<GeologySectionRun> runsForColumn(
            Map<ChunkCoordinate, ParsedChunk> chunks,
            Map<Integer, BlockInfo> blockRegistry,
            int worldX,
            int worldZ,
            VerticalBounds bounds
    ) {
        if (bounds.minYInclusive()
                == bounds.maxYExclusive()) {

            return List.of();
        }

        List<GeologySectionRun> runs =
                new ArrayList<>();

        SampleState current =
                null;

        int runStartY =
                bounds.minYInclusive();

        for (int worldY = bounds.minYInclusive();
             worldY < bounds.maxYExclusive();
             worldY++) {

            SampleState sample =
                    sample(
                            chunks,
                            blockRegistry,
                            worldX,
                            worldY,
                            worldZ
                    );

            if (current == null) {
                current =
                        sample;

                runStartY =
                        worldY;

                continue;
            }

            if (!current.equals(
                    sample
            )) {
                runs.add(
                        toRun(
                                runStartY,
                                worldY,
                                current
                        )
                );

                current =
                        sample;

                runStartY =
                        worldY;
            }
        }

        runs.add(
                toRun(
                        runStartY,
                        bounds.maxYExclusive(),
                        current
                )
        );

        return List.copyOf(
                runs
        );
    }

    private SampleState sample(
            Map<ChunkCoordinate, ParsedChunk> chunks,
            Map<Integer, BlockInfo> blockRegistry,
            int worldX,
            int worldY,
            int worldZ
    ) {
        int chunkX =
                Math.floorDiv(
                        worldX,
                        ChunkCoordinate.SIZE_BLOCKS
                );

        int chunkY =
                Math.floorDiv(
                        worldY,
                        ChunkCoordinate.SIZE_BLOCKS
                );

        int chunkZ =
                Math.floorDiv(
                        worldZ,
                        ChunkCoordinate.SIZE_BLOCKS
                );

        ParsedChunk chunk =
                chunks.get(
                        new ChunkCoordinate(
                                chunkX,
                                chunkY,
                                chunkZ
                        )
                );

        if (chunk == null) {
            return SampleState.unavailable();
        }

        int localX =
                Math.floorMod(
                        worldX,
                        ChunkCoordinate.SIZE_BLOCKS
                );

        int localY =
                worldY
                        - chunk.minY();

        int localZ =
                Math.floorMod(
                        worldZ,
                        ChunkCoordinate.SIZE_BLOCKS
                );

        if (localX < 0
                || localX >= chunk.sizeX()
                || localY < 0
                || localY >= chunk.sizeY()
                || localZ < 0
                || localZ >= chunk.sizeZ()) {

            return SampleState.unavailable();
        }

        int blockId =
                chunk.blockIdAt(
                        localX,
                        localY,
                        localZ
                );

        BlockInfo block =
                blockRegistry.getOrDefault(
                        blockId,
                        BlockInfo.unknown(
                                blockId
                        )
                );

        String blockCode =
                block.code();

        if (blockCode == null
                || blockCode.isBlank()) {

            blockCode =
                    "unknown:"
                            + blockId;
        }

        return SampleState.observed(
                blockId,
                blockCode
        );
    }

    private GeologySectionRun toRun(
            int minYInclusive,
            int maxYExclusive,
            SampleState state
    ) {
        return new GeologySectionRun(
                minYInclusive,
                maxYExclusive,
                state.observed(),
                state.blockId(),
                state.blockCode()
        );
    }

    private List<HorizontalPoint> rasterize(
            int startWorldX,
            int startWorldZ,
            int endWorldX,
            int endWorldZ
    ) {
        List<HorizontalPoint> points =
                new ArrayList<>();

        int worldX =
                startWorldX;

        int worldZ =
                startWorldZ;

        long deltaX =
                Math.abs(
                        (long) endWorldX
                                - startWorldX
                );

        long deltaZ =
                Math.abs(
                        (long) endWorldZ
                                - startWorldZ
                );

        int stepX =
                Integer.compare(
                        endWorldX,
                        startWorldX
                );

        int stepZ =
                Integer.compare(
                        endWorldZ,
                        startWorldZ
                );

        long error =
                deltaX
                        - deltaZ;

        while (true) {
            points.add(
                    new HorizontalPoint(
                            worldX,
                            worldZ
                    )
            );

            if (worldX == endWorldX
                    && worldZ == endWorldZ) {

                break;
            }

            long doubledError =
                    error
                            * 2L;

            if (doubledError > -deltaZ) {
                error -=
                        deltaZ;

                worldX +=
                        stepX;
            }

            if (doubledError < deltaX) {
                error +=
                        deltaX;

                worldZ +=
                        stepZ;
            }
        }

        return List.copyOf(
                points
        );
    }

    private record HorizontalPoint(
            int worldX,
            int worldZ
    ) {
    }

    private record HorizontalChunkKey(
            int chunkX,
            int chunkZ
    ) {
    }

    private record VerticalBounds(
            int minYInclusive,
            int maxYExclusive
    ) {
    }

    private record SampleState(
            boolean observed,
            int blockId,
            String blockCode
    ) {

        private static SampleState observed(
                int blockId,
                String blockCode
        ) {
            return new SampleState(
                    true,
                    blockId,
                    blockCode
            );
        }

        private static SampleState unavailable() {
            return new SampleState(
                    false,
                    -1,
                    "unavailable"
            );
        }
    }
}
