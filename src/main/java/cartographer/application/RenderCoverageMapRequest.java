package cartographer.application;

import java.nio.file.Path;
import java.util.Objects;

public record RenderCoverageMapRequest(Path savePath) {
    public RenderCoverageMapRequest {
        Objects.requireNonNull(savePath, "save path is required");
    }
}
