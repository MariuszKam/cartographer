package cartographer.save;

import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;

final class VcdbsReaderFixtures {
    private VcdbsReaderFixtures() {
    }

    static VcdbsReader withChunkParser(ChunkParser chunkParser) {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                chunkParser,
                new RegistryParser()
        );
    }

    static VcdbsReader withChunkParser(
            ChunkParser chunkParser,
            int workerCount,
            int maxInFlight
    ) {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                chunkParser,
                new RegistryParser(),
                workerCount,
                maxInFlight
        );
    }

    static VcdbsReader withMapChunkParser(MapChunkParser mapChunkParser) {
        return new VcdbsReader(
                new PlayerDataParser(),
                mapChunkParser,
                new ChunkParser(),
                new RegistryParser()
        );
    }
}
