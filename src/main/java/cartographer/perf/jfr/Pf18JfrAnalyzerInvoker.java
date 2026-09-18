package cartographer.perf.jfr;

import java.nio.file.Path;

@FunctionalInterface
interface Pf18JfrAnalyzerInvoker {
    Pf18JfrSummary analyze(Path recording, Path summary, Pf18JfrCampaignIdentity identity);
}
