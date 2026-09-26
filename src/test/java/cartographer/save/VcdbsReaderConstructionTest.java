package cartographer.save;

import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class VcdbsReaderConstructionTest {

    @Test
    void rejectsMissingParserCollaborators() {
        PlayerDataParser player = new PlayerDataParser();
        MapChunkParser mapChunk = new MapChunkParser();
        ChunkParser chunk = new ChunkParser();
        RegistryParser registry = new RegistryParser();

        assertThrows(
                NullPointerException.class,
                () -> new VcdbsReader(null, mapChunk, chunk, registry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new VcdbsReader(player, null, chunk, registry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new VcdbsReader(player, mapChunk, null, registry)
        );
        assertThrows(
                NullPointerException.class,
                () -> new VcdbsReader(player, mapChunk, chunk, null)
        );
    }
}
