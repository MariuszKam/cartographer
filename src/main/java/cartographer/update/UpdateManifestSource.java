package cartographer.update;

import java.io.IOException;

@FunctionalInterface
public interface UpdateManifestSource {
    String load() throws IOException, InterruptedException;
}
