package cartographer.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpUpdateManifestSource implements UpdateManifestSource {
    @FunctionalInterface
    interface Sender {
        HttpResponse<String> send(HttpRequest request)
                throws IOException, InterruptedException;
    }

    private final Sender sender;
    private final HttpRequest request;

    public HttpUpdateManifestSource(
            URI manifestUri,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        this(
                manifestUri,
                createSender(connectTimeout),
                requestTimeout
        );
    }

    HttpUpdateManifestSource(
            URI manifestUri,
            Sender sender,
            Duration requestTimeout
    ) {
        Objects.requireNonNull(manifestUri, "manifestUri is required");
        this.sender = Objects.requireNonNull(sender, "sender is required");
        Objects.requireNonNull(requestTimeout, "requestTimeout is required");

        if (!"https".equalsIgnoreCase(manifestUri.getScheme())
                || !"github.com".equalsIgnoreCase(manifestUri.getHost())) {
            throw new IllegalArgumentException(
                    "Update manifest URI must use https://github.com"
            );
        }

        request = HttpRequest.newBuilder(manifestUri)
                .timeout(requestTimeout)
                .header("Accept", "text/plain")
                .header("User-Agent", "VS-Cartographer-Updater")
                .GET()
                .build();
    }

    private static Sender createSender(Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout, "connectTimeout is required");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return request -> client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    @Override
    public String load() throws IOException, InterruptedException {
        HttpResponse<String> response = sender.send(request);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "Update manifest request returned HTTP "
                            + response.statusCode()
            );
        }
        TrustedUpdateUriPolicy.requireTrustedGithubHttpsUri(
                response.uri(),
                "Update manifest redirect left trusted GitHub HTTPS hosts"
        );
        return response.body();
    }
}
