package cartographer.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Consumer;

public final class HttpUpdateInstallerSource implements UpdateInstallerSource {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpUpdateInstallerSource(
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        Objects.requireNonNull(connectTimeout, "connectTimeout is required");
        this.requestTimeout = Objects.requireNonNull(
                requestTimeout,
                "requestTimeout is required"
        );

        client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void download(
            UpdateManifest manifest,
            Path destination,
            Consumer<UpdateDownloadProgress> progressListener
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(manifest, "manifest is required");
        Objects.requireNonNull(destination, "destination is required");
        Objects.requireNonNull(
                progressListener,
                "progressListener is required"
        );

        HttpRequest request = HttpRequest.newBuilder(manifest.installerUri())
                .timeout(requestTimeout)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "VS-Cartographer-Updater")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Installer request returned HTTP "
                                + response.statusCode()
                );
            }
            requireTrustedFinalUri(response.uri());

            OptionalLong contentLength = response.headers()
                    .firstValueAsLong("Content-Length");
            if (contentLength.isPresent()
                    && contentLength.getAsLong() != manifest.installerSize()) {
                throw new IOException(
                        "Installer Content-Length does not match manifest"
                );
            }

            progressListener.accept(new UpdateDownloadProgress(
                    0L,
                    manifest.installerSize()
            ));

            try (OutputStream output = Files.newOutputStream(
                    destination,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = 0L;
                while (true) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException(
                                "Installer download interrupted"
                        );
                    }

                    int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }

                    downloaded += read;
                    if (downloaded > manifest.installerSize()) {
                        throw new IOException(
                                "Installer exceeded manifest size"
                        );
                    }

                    output.write(buffer, 0, read);
                    progressListener.accept(new UpdateDownloadProgress(
                            downloaded,
                            manifest.installerSize()
                    ));
                }

                if (downloaded != manifest.installerSize()) {
                    throw new IOException(
                            "Installer size does not match manifest"
                    );
                }
            }
        }
    }

    private void requireTrustedFinalUri(URI uri) throws IOException {
        if (!isTrustedDownloadUri(uri)) {
            throw new IOException(
                    "Installer redirect left trusted GitHub HTTPS hosts"
            );
        }
    }

    static boolean isTrustedDownloadUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "github.com".equals(normalizedHost)
                || normalizedHost.endsWith(".githubusercontent.com");
    }
}
