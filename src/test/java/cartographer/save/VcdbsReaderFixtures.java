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

    static VcdbsReader withTwoDecodeWorkers(
            ChunkParser chunkParser
    ) {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                chunkParser,
                new RegistryParser(),
                2,
                4
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
