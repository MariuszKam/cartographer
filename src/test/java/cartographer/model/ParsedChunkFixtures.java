package cartographer.model;

public final class ParsedChunkFixtures {
    private ParsedChunkFixtures() {
    }

    public static ParsedChunk create(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] blockIds
    ) {
        return create(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockIds,
                new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)],
                0,
                true,
                ""
        );
    }

    public static ParsedChunk create(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] blockIds,
            int[] liquidIds,
            int savedCompressionVersion
    ) {
        return create(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                blockIds,
                liquidIds,
                savedCompressionVersion,
                true,
                ""
        );
    }

    public static ParsedChunk create(
            ChunkCoordinate coordinate,
            int minY,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] blockIds,
            int[] liquidIds,
            int savedCompressionVersion,
            boolean liquidLayerAvailable,
            String liquidDecodeError
    ) {
        DecodedChunkLayer liquids = liquidIds == null
                ? null
                : DecodedChunkLayer.copyOf(liquidIds);
        return ParsedChunk.fromDecodedLayers(
                coordinate,
                minY,
                sizeX,
                sizeY,
                sizeZ,
                DecodedChunkLayer.copyOf(blockIds),
                liquids,
                savedCompressionVersion,
                liquidLayerAvailable,
                liquidDecodeError
        );
    }

    public static int[] liquidIds(ParsedChunk chunk) {
        int[] values = new int[Math.multiplyExact(
                Math.multiplyExact(chunk.sizeX(), chunk.sizeY()),
                chunk.sizeZ()
        )];
        int index = 0;
        for (int y = 0; y < chunk.sizeY(); y++) {
            for (int z = 0; z < chunk.sizeZ(); z++) {
                for (int x = 0; x < chunk.sizeX(); x++) {
                    values[index++] = chunk.liquidIdAt(x, y, z);
                }
            }
        }
        return values;
    }
}
