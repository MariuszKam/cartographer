package cartographer.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckServiceTest {

    @Test
    void reportsNewerVersionAsAvailable() {
        UpdateCheckService service = service(
                "1.0.0",
                () -> UpdateManifestParserTest.validManifest("1.1.0")
        );

        UpdateCheckResult result = service.check();

        assertEquals(
                UpdateCheckResult.Status.UPDATE_AVAILABLE,
                result.status()
        );
        assertEquals(
                ApplicationVersion.parse("1.1.0"),
                result.manifest().orElseThrow().version()
        );
    }

    @Test
    void treatsEqualAndOlderRemoteVersionsAsUpToDate() {
        assertEquals(
                UpdateCheckResult.Status.UP_TO_DATE,
                service(
                        "1.1.0",
                        () -> UpdateManifestParserTest.validManifest("1.1.0")
                ).check().status()
        );
        assertEquals(
                UpdateCheckResult.Status.UP_TO_DATE,
                service(
                        "1.1.0",
                        () -> UpdateManifestParserTest.validManifest("1.0.9")
                ).check().status()
        );
    }

    @Test
    void convertsNetworkAndManifestFailuresToCheckFailure() {
        UpdateCheckResult networkFailure = service(
                "1.0.0",
                () -> {
                    throw new IOException("offline");
                }
        ).check();
        assertEquals(
                UpdateCheckResult.Status.CHECK_FAILED,
                networkFailure.status()
        );
        assertTrue(
                networkFailure.failureMessage().orElseThrow()
                        .contains("offline")
        );

        UpdateCheckResult parseFailure = service(
                "1.0.0",
                () -> "not-a-manifest"
        ).check();
        assertEquals(
                UpdateCheckResult.Status.CHECK_FAILED,
                parseFailure.status()
        );
    }

    private UpdateCheckService service(
            String currentVersion,
            UpdateManifestSource source
    ) {
        return new UpdateCheckService(
                ApplicationVersion.parse(currentVersion),
                source,
                new UpdateManifestParser()
        );
    }
}
