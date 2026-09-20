package cartographer.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpUpdateManifestSource implements UpdateManifestSource {
    private final HttpClient client;
    private final HttpRequest request;

    public HttpUpdateManifestSource(
            URI manifestUri,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        Objects.requireNonNull(manifestUri, "manifestUri is required");
        Objects.requireNonNull(connectTimeout, "connectTimeout is required");
        Objects.requireNonNull(requestTimeout, "requestTimeout is required");

        if (!"https".equalsIgnoreCase(manifestUri.getScheme())
                || !"github.com".equalsIgnoreCase(manifestUri.getHost())) {
            throw new IllegalArgumentException(
                    "Update manifest URI must use https://github.com"
            );
        }

        client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        request = HttpRequest.newBuilder(manifestUri)
                .timeout(requestTimeout)
                .header("Accept", "text/plain")
                .header("User-Agent", "VS-Cartographer-Updater")
                .GET()
                .build();
    }

    @Override
    public String load() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Update manifest request returned HTTP "
                            + response.statusCode()
            );
        }
        return response.body();
    }
}
