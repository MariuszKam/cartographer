package cartographer.perf.macro;

import cartographer.perf.baseline.ReferenceBaseline;

import java.nio.file.Path;
import java.util.Objects;

/** Successful macro baseline evidence and its deterministic output path. */
public record MacroBaselineResult(ReferenceBaseline baseline, Path reportPath) {
    public MacroBaselineResult {
        Objects.requireNonNull(baseline, "baseline is required");
        Objects.requireNonNull(reportPath, "report path is required");
    }
}
