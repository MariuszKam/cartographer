package cartographer.perf;

import java.util.Map;

public record IncrementalState(String cacheKey, Map<String, String> tableFingerprints) {
}
