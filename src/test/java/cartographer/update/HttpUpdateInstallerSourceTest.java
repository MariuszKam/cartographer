package cartographer.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUpdateInstallerSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsSuccessfulResponseToDestinationAndReportsProgress()
            throws Exception {
        byte[] bytes = "installer-payload".getBytes();
        UpdateManifest manifest = manifest(bytes);
        AtomicReference<HttpRequest> request = new AtomicReference<>();
        AtomicReference<UpdateDownloadProgress> progress =
                new AtomicReference<>();
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                requested -> {
                    request.set(requested);
                    return response(
                            requested,
                            200,
                            manifest.installerUri(),
                            Map.of(),
                            bytes
                    );
                },
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        source.download(manifest, destination, progress::set);

        assertEquals(manifest.installerUri(), request.get().uri());
        assertEquals(
                Optional.of(Duration.ofSeconds(5)),
                request.get().timeout()
        );
        assertArrayEquals(bytes, Files.readAllBytes(destination));
        assertEquals(100, progress.get().percent());
    }

    @Test
    void httpPayloadIsVerifiedAndPromotedByDownloadService()
            throws Exception {
        byte[] bytes = "verified-http-installer".getBytes();
        UpdateManifest manifest = manifest(bytes);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        manifest.installerUri(),
                        Map.of(
                                "Content-Length",
                                List.of(Long.toString(bytes.length))
                        ),
                        bytes
                ),
                Duration.ofSeconds(5)
        );
        Path updates = temporaryDirectory.resolve("updates");
        UpdateDownloadService service = new UpdateDownloadService(
                updates,
                source
        );

        UpdateDownloadResult result = service.download(
                manifest,
                ignored -> { }
        );

        assertEquals(UpdateDownloadResult.Status.READY, result.status());
        Path installer = result.installerPath().orElseThrow();
        assertArrayEquals(bytes, Files.readAllBytes(installer));
        assertFalse(Files.exists(
                installer.resolveSibling(
                        installer.getFileName().toString() + ".part"
                )
        ));
    }

    @Test
    void rejectsNonSuccessStatusBeforeCreatingDestination() {
        byte[] bytes = "installer-payload".getBytes();
        UpdateManifest manifest = manifest(bytes);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        404,
                        manifest.installerUri(),
                        Map.of(),
                        new byte[0]
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
        assertFalse(Files.exists(destination));
    }

    @Test
    void rejectsMismatchedContentLengthBeforeCreatingDestination() {
        byte[] bytes = "installer-payload".getBytes();
        UpdateManifest manifest = manifest(bytes);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        manifest.installerUri(),
                        Map.of(
                                "Content-Length",
                                List.of(Long.toString(bytes.length + 1L))
                        ),
                        bytes
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
        assertFalse(Files.exists(destination));
    }

    @Test
    void rejectsPayloadThatEndsBeforeManifestSize() {
        byte[] expected = "expected-installer".getBytes();
        byte[] truncated = "short".getBytes();
        UpdateManifest manifest = manifest(expected);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        manifest.installerUri(),
                        Map.of(),
                        truncated
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
    }

    @Test
    void rejectsPayloadThatExceedsManifestSize() throws Exception {
        byte[] expected = "expected".getBytes();
        byte[] oversized = "expected-extra".getBytes();
        UpdateManifest manifest = manifest(expected);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        manifest.installerUri(),
                        Map.of(),
                        oversized
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
        assertEquals(0L, Files.size(destination));
    }

    @Test
    void acceptsGithubAndGithubusercontentHttpsTargets() {
        assertTrue(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create(
                        "https://github.com/MariuszKam/cartographer/"
                                + "releases/download/v1.1.0/file.exe"
                )
        ));
        assertTrue(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create(
                        "https://release-assets.githubusercontent.com/"
                                + "github-production-release-asset/file"
                )
        ));
        assertTrue(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create(
                        "https://objects.githubusercontent.com/"
                                + "github-production-release-asset/file"
                )
        ));
    }

    @SuppressWarnings("HttpUrlsUsage")
    @Test
    void rejectsNonHttpsAndNonGithubTargets() {
        assertFalse(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create(
                        "http://github.com/MariuszKam/cartographer/"
                                + "releases/download/v1.1.0/file.exe"
                )
        ));
        assertFalse(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create("https://example.com/file.exe")
        ));
        assertFalse(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create("https://evilgithubusercontent.com/file.exe")
        ));
        assertFalse(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create(
                        "https://githubusercontent.com.evil.example/file.exe"
                )
        ));
        assertFalse(TrustedUpdateUriPolicy.isTrustedGithubHttpsUri(
                URI.create("https://githubusercontent.com/file.exe")
        ));
    }

    @Test
    void rejectsHttpsRedirectOutsideTrustedGithubHosts() {
        byte[] bytes = "installer-payload".getBytes();
        UpdateManifest manifest = manifest(bytes);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        URI.create("https://example.com/update.exe"),
                        Map.of(),
                        bytes
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
        assertFalse(Files.exists(destination));
    }

    @SuppressWarnings("HttpUrlsUsage")
    @Test
    void rejectsRedirectThatDoesNotRemainOnHttps() {
        byte[] bytes = "installer-payload".getBytes();
        UpdateManifest manifest = manifest(bytes);
        HttpUpdateInstallerSource source = new HttpUpdateInstallerSource(
                request -> response(
                        request,
                        200,
                        URI.create(
                                "http://github.com/MariuszKam/cartographer/"
                                        + "releases/download/v1.1.0/"
                                        + manifest.installerFile()
                        ),
                        Map.of(),
                        bytes
                ),
                Duration.ofSeconds(5)
        );
        Path destination = temporaryDirectory.resolve("installer.exe.part");

        assertThrows(
                IOException.class,
                () -> source.download(manifest, destination, ignored -> { })
        );
        assertFalse(Files.exists(destination));
    }

    private static UpdateManifest manifest(byte[] installerBytes) {
        String file = "VS-Cartographer-Setup-1.1.0.exe";
        return new UpdateManifest(
                1,
                "stable",
                ApplicationVersion.parse("1.1.0"),
                file,
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "download/v1.1.0/" + file
                ),
                sha256(installerBytes),
                installerBytes.length,
                URI.create(
                        "https://github.com/MariuszKam/cartographer/releases/"
                                + "tag/v1.1.0"
                )
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpResponse<InputStream> response(
            HttpRequest request,
            int status,
            URI uri,
            Map<String, List<String>> headers,
            byte[] body
    ) {
        HttpHeaders httpHeaders = HttpHeaders.of(
                headers,
                (name, value) -> true
        );
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return httpHeaders;
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
