package cartographer.perf.lab;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceLabConfigurationTest {
    private static final String SHA = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";

    @Test
    void argumentsAreExplicitAndNormalized() {
        PerformanceLabConfiguration configuration = PerformanceLabConfiguration.fromArgs(
                new String[]{
                        "--save", "relative/world.vcdbs",
                        "--git-sha", " " + SHA + " ",
                        "--report", "build/perf/reports/report.txt"
                }
        );

        assertEquals(Path.of("relative/world.vcdbs").toAbsolutePath().normalize(),
                configuration.savePath());
        assertEquals(SHA.toLowerCase(java.util.Locale.ROOT), configuration.gitCommitSha());
        assertEquals(Path.of("build/perf/reports/report.txt").toAbsolutePath().normalize(),
                configuration.reportPath());
    }

    @Test
    void requiredArgumentsAndExactGitShaAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceLabConfiguration.fromArgs(new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceLabConfiguration.fromArgs(new String[]{"--save", "world.vcdbs"}));
        assertThrows(IllegalArgumentException.class,
                () -> new PerformanceLabConfiguration(
                        Path.of("world.vcdbs"), "latest", Path.of("report.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceLabConfiguration.fromArgs(new String[]{
                        "--save", "world.vcdbs", "--git-sha", SHA, "--report"
                }));
    }

    @Test
    void duplicateAndUnknownArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceLabConfiguration.fromArgs(new String[]{
                        "--save", "one.vcdbs", "--save", "two.vcdbs",
                        "--git-sha", SHA, "--report", "report.txt"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceLabConfiguration.fromArgs(new String[]{
                        "--save", "world.vcdbs", "--git-sha", SHA,
                        "--report", "report.txt", "--unknown", "value"
                }));
    }
}
