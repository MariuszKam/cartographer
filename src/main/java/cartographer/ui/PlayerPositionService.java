package cartographer.ui;

import cartographer.model.DisplayPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.Objects;

public class PlayerPositionService {

    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;

    public PlayerPositionService(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(
                metadataReader,
                "metadataReader is required"
        );
    }

    public PlayerPositionView load(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");

        WorldPosition absolute = reader.readPlayerPosition(savePath);
        WorldMetadata metadata = metadataReader.read(savePath);
        DisplayPosition display = metadata.toDisplay(absolute);
        var chunk = absolute.chunkCoordinate();

        return new PlayerPositionView(
                display.x(),
                display.y(),
                display.z(),
                chunk.x(),
                chunk.z()
        );
    }
}
