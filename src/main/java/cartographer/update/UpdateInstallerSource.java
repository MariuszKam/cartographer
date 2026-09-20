package cartographer.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

@FunctionalInterface
public interface UpdateInstallerSource {
    void download(
            UpdateManifest manifest,
            Path destination,
            Consumer<UpdateDownloadProgress> progressListener
    ) throws IOException, InterruptedException;
}
