package cartographer.update;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpUpdateManifestSourceTest {
    private static final URI MANIFEST_URI = URI.create(
            "https://github.com/MariuszKam/cartographer/releases/"
                    + "latest/download/update.properties"
    );

    @Test
    void loadsSuccessfulManifestFromTrustedGithubTarget() throws Exception {
        AtomicReference<HttpRequest> request = new AtomicReference<>();
        HttpUpdateManifestSource source = new HttpUpdateManifestSource(
                MANIFEST_URI,
                requested -> {
                    request.set(requested);
                    return response(
                            requested,
                            200,
                            MANIFEST_URI,
                            "manifest-body"
                    );
                },
                Duration.ofSeconds(5)
        );

        assertEquals("manifest-body", source.load());
        assertEquals(MANIFEST_URI, request.get().uri());
        assertEquals(
                Optional.of(Duration.ofSeconds(5)),
                request.get().timeout()
        );
        assertEquals(
                Optional.of("text/plain"),
                request.get().headers().firstValue("Accept")
        );
        assertEquals(
                Optional.of("VS-Cartographer-Updater"),
                request.get().headers().firstValue("User-Agent")
        );
    }

    @Test
    void acceptsTrustedGithubusercontentRedirectTarget() throws Exception {
        HttpUpdateManifestSource source = sourceReturning(
                200,
                URI.create(
                        "https://release-assets.githubusercontent.com/"
                                + "github-production-release-asset/manifest"
                ),
                "manifest-body"
        );

        assertEquals("manifest-body", source.load());
    }

    @Test
    void rejectsSuccessfulResponseFromUntrustedHttpsTarget() {
        HttpUpdateManifestSource source = sourceReturning(
                200,
                URI.create("https://example.com/update.properties"),
                "untrusted-manifest"
        );

        assertThrows(IOException.class, source::load);
    }

    @SuppressWarnings("HttpUrlsUsage")
    @Test
    void rejectsSuccessfulResponseThatDoesNotRemainOnHttps() {
        HttpUpdateManifestSource source = sourceReturning(
                200,
                URI.create(
                        "http://github.com/MariuszKam/cartographer/"
                                + "releases/latest/download/update.properties"
                ),
                "untrusted-manifest"
        );

        assertThrows(IOException.class, source::load);
    }

    @SuppressWarnings("HttpUrlsUsage")
    @Test
    void rejectsInitialManifestUriOutsideGithubHttps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpUpdateManifestSource(
                        URI.create("https://example.com/update.properties"),
                        request -> response(
                                request,
                                200,
                                URI.create("https://example.com/update.properties"),
                                "manifest"
                        ),
                        Duration.ofSeconds(5)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new HttpUpdateManifestSource(
                        URI.create(
                                "http://github.com/MariuszKam/cartographer/"
                                        + "releases/latest/download/update.properties"
                        ),
                        request -> response(
                                request,
                                200,
                                MANIFEST_URI,
                                "manifest"
                        ),
                        Duration.ofSeconds(5)
                )
        );
    }

    @Test
    void rejectsNonSuccessStatus() {
        HttpUpdateManifestSource source = sourceReturning(
                404,
                MANIFEST_URI,
                "not-found"
        );

        assertThrows(IOException.class, source::load);
    }

    private static HttpUpdateManifestSource sourceReturning(
            int status,
            URI responseUri,
            String body
    ) {
        return new HttpUpdateManifestSource(
                MANIFEST_URI,
                request -> response(request, status, responseUri, body),
                Duration.ofSeconds(5)
        );
    }

    private static HttpResponse<String> response(
            HttpRequest request,
            int status,
            URI uri,
            String body
    ) {
        HttpHeaders headers = HttpHeaders.of(
                Map.of(),
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
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public String body() {
                return body;
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
