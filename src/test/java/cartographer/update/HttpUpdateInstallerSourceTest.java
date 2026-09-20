package cartographer.update;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpUpdateInstallerSourceTest {

    @Test
    void acceptsGithubAndGithubusercontentHttpsTargets() {
        assertTrue(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("https://github.com/MariuszKam/cartographer/releases/download/v1.1.0/file.exe")
        ));
        assertTrue(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("https://release-assets.githubusercontent.com/github-production-release-asset/file")
        ));
        assertTrue(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("https://objects.githubusercontent.com/github-production-release-asset/file")
        ));
    }

    @Test
    void rejectsNonHttpsAndNonGithubTargets() {
        assertFalse(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("http://github.com/MariuszKam/cartographer/releases/download/v1.1.0/file.exe")
        ));
        assertFalse(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("https://example.com/file.exe")
        ));
        assertFalse(HttpUpdateInstallerSource.isTrustedDownloadUri(
                URI.create("https://evilgithubusercontent.com/file.exe")
        ));
    }
}
