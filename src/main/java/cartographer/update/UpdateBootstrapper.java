package cartographer.update;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface UpdateBootstrapper {
    void launch(
            UpdateManifest manifest,
            Path installerPath
    ) throws IOException;
}
