package cartographer.perf.gui;

import java.util.List;
import java.util.Objects;

public record GuiReleaseGateReport(
        String candidateSha,
        List<String> passedEvidence,
        List<String> failures
) {
    public GuiReleaseGateReport {
        candidateSha = GuiManualValidationManifest.exactSha(candidateSha);
        passedEvidence = List.copyOf(
                Objects.requireNonNull(passedEvidence, "passedEvidence is required")
        );
        failures = List.copyOf(
                Objects.requireNonNull(failures, "failures are required")
        );
    }

    public boolean accepted() {
        return failures.isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("VS Cartographer GUI-P14 Release Gate\n");
        out.append("Candidate SHA: ").append(candidateSha).append('\n');
        out.append("Verdict: ")
                .append(accepted() ? "PASS" : "FAIL")
                .append('\n');
        out.append("Accepted evidence:\n");
        if (passedEvidence.isEmpty()) {
            out.append("- none\n");
        } else {
            passedEvidence.forEach(value ->
                    out.append("- ").append(value).append('\n'));
        }
        out.append("Failures:\n");
        if (failures.isEmpty()) {
            out.append("- none\n");
        } else {
            failures.forEach(value ->
                    out.append("- ").append(value).append('\n'));
        }
        return out.toString();
    }
}
