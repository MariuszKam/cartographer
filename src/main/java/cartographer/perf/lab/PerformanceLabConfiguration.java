package cartographer.perf.lab;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Explicit local-run inputs supplied by the opt-in Gradle task. */
public record PerformanceLabConfiguration(
        Path savePath,
        String gitCommitSha,
        Path reportPath
) {
    public PerformanceLabConfiguration {
        Objects.requireNonNull(savePath, "savePath is required");
        savePath = savePath.toAbsolutePath().normalize();
        gitCommitSha = exactGitSha(gitCommitSha);
        Objects.requireNonNull(reportPath, "reportPath is required");
        reportPath = reportPath.toAbsolutePath().normalize();
    }

    public static PerformanceLabConfiguration fromArgs(String[] args) {
        Objects.requireNonNull(args, "args is required");
        Path savePath = null;
        String gitCommitSha = null;
        Path reportPath = null;
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[index]);
            }
            switch (args[index]) {
                case "--save" -> {
                    if (savePath != null) {
                        throw new IllegalArgumentException("Duplicate --save argument");
                    }
                    savePath = Path.of(args[index + 1]);
                }
                case "--git-sha" -> {
                    if (gitCommitSha != null) {
                        throw new IllegalArgumentException("Duplicate --git-sha argument");
                    }
                    gitCommitSha = args[index + 1];
                }
                case "--report" -> {
                    if (reportPath != null) {
                        throw new IllegalArgumentException("Duplicate --report argument");
                    }
                    reportPath = Path.of(args[index + 1]);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
            }
        }
        if (savePath == null || gitCommitSha == null || reportPath == null) {
            throw new IllegalArgumentException(
                    "Required arguments: --save <path> --git-sha <sha> --report <path>"
            );
        }
        return new PerformanceLabConfiguration(savePath, gitCommitSha, reportPath);
    }

    private static String exactGitSha(String value) {
        Objects.requireNonNull(value, "gitCommitSha is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitCommitSha must be a full 40-character SHA");
        }
        return normalized;
    }
}
