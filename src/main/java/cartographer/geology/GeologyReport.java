package cartographer.geology;

import java.util.Map;

public record GeologyReport(
        int samples,
        int geologicalSamples,
        int unknownSamples,
        Map<String, Integer> rockFamilies,
        Map<String, Integer> materialTypes) {
}
