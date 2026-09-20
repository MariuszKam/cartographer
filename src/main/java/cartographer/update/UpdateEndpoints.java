package cartographer.update;

import java.net.URI;

public final class UpdateEndpoints {
    private static final URI LATEST_STABLE_MANIFEST = URI.create(
            "https://github.com/MariuszKam/cartographer/releases/latest/download/update.properties"
    );

    private UpdateEndpoints() {
    }

    public static URI latestStableManifest() {
        return LATEST_STABLE_MANIFEST;
    }
}
