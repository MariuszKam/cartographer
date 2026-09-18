package cartographer.perf.gui;

import cartographer.perf.safety.Pf18SourceSafetyReport;
import cartographer.perf.safety.Pf18SourceSafetyRunner;
import cartographer.perf.safety.RealSaveValidationException;
import cartographer.perf.safety.RealSaveValidationRunner;
import cartographer.perf.safety.SaveSafetyResult;
import cartographer.perf.safety.SaveSafetyStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class GuiSourceSafetyEvidenceMain {
    private GuiSourceSafetyEvidenceMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: guiSourceSafetyEvidence "
                            + "<save> <cacheRoot> <gitSha> <evidenceRoot>"
            );
        }

        Path save = Path.of(args[0]).toAbsolutePath().normalize();
        Path cache = Path.of(args[1]).toAbsolutePath().normalize();
        String sha = GuiManualValidationManifest.exactSha(args[2]);
        Path root = Path.of(args[3]).toAbsolutePath().normalize();
        requireExternalEvidenceRoot(save, root);

        SaveSafetyStatus realStatus = SaveSafetyStatus.FAIL;
        Optional<String> realFailure = Optional.empty();
        try {
            SaveSafetyResult real =
                    new RealSaveValidationRunner().validate(save);
            realStatus = real.status();
        } catch (RealSaveValidationException failure) {
            realStatus = failure.safetyResult()
                    .map(SaveSafetyResult::status)
                    .orElse(SaveSafetyStatus.FAIL);
            realFailure = Optional.of(failure.toString());
        } catch (RuntimeException failure) {
            realFailure = Optional.of(failure.toString());
        }

        Pf18SourceSafetyReport pf18 =
                new Pf18SourceSafetyRunner().validate(save, cache);

        StringBuilder out = new StringBuilder();
        line(out, "candidateSha", sha);
        line(out, "realSaveStatus", realStatus.name());
        line(out, "pf18Status", pf18.status().name());
        line(
                out,
                "operationCompleted",
                Boolean.toString(pf18.operationCompleted())
        );
        line(
                out,
                "cacheContained",
                Boolean.toString(pf18.cacheEvidence().contained())
        );
        line(
                out,
                "qualifyingManifest",
                Boolean.toString(
                        pf18.cacheEvidence().qualifyingManifest()
                )
        );
        line(
                out,
                "realSaveFailure",
                escape(realFailure.orElse(""))
        );
        line(
                out,
                "pf18Failure",
                escape(pf18.failure().orElse(""))
        );

        try {
            Files.createDirectories(root);
            Path output = root.resolve(
                    GuiReleaseGate.SOURCE_SAFETY_FILE
            );
            Files.writeString(
                    output,
                    out.toString(),
                    StandardCharsets.UTF_8
            );
            System.out.println(
                    "GUI source-safety evidence: " + output
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot write GUI source-safety evidence",
                    exception
            );
        }

        if (realStatus != SaveSafetyStatus.PASS || !pf18.accepted()) {
            throw new IllegalStateException(
                    "GUI source-safety evidence did not pass"
            );
        }
    }

    private static void line(
            StringBuilder out,
            String key,
            String value
    ) {
        out.append(key).append('=').append(value).append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
    private static void requireExternalEvidenceRoot(
            Path save,
            Path root
    ) {
        Path sourceDirectory = java.util.Objects.requireNonNull(
                save.getParent(),
                "save parent is required"
        ).toAbsolutePath().normalize();
        if (root.equals(save)
                || root.startsWith(sourceDirectory)
                || sourceDirectory.startsWith(root)) {
            throw new IllegalArgumentException(
                    "evidenceRoot must be outside the source save directory: "
                            + root
            );
        }
    }

}
