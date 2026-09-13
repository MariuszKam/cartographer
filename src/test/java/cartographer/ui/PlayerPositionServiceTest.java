package cartographer.ui;

import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPositionServiceTest {

    @Test
    void convertsAbsolutePositionThroughWorldMetadata() {
        PlayerPositionService service = new PlayerPositionService(
                new FakeReader(),
                new FakeMetadataReader()
        );

        PlayerPositionView view = service.load(Path.of("world.vcdbs"));

        assertEquals(-1081.8, view.x(), 0.0001);
        assertEquals(111.0, view.y(), 0.0001);
        assertEquals(-60.1, view.z(), 0.0001);
        assertEquals(15966, view.chunkX());
        assertEquals(15998, view.chunkZ());
    }

    private static class FakeReader extends VcdbsReader {
        FakeReader() {
            super(
                    new PlayerDataParser(),
                    new MapChunkParser(),
                    new ChunkParser(),
                    new RegistryParser()
            );
        }

        @Override
        public WorldPosition readPlayerPosition(
                Path savePath,
                cartographer.cli.ProgressReporter progress
        ) {
            return new WorldPosition(510918.2, 111.0, 511939.9);
        }
    }

    private static class FakeMetadataReader extends WorldMetadataReader {
        @Override
        public WorldMetadata read(
                Path savePath,
                cartographer.cli.ProgressReporter progress
        ) {
            return new WorldMetadata(1024000, 256, 1024000);
        }
    }
}
