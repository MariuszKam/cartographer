package cartographer.resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable catalogue containing only resources observed in the scanned area. */
public final class ObservedSurfaceResourceCatalog {
    private final List<ObservedSurfaceResource> resources;
    private final Map<String, ObservedSurfaceResource> resourcesByKey;

    ObservedSurfaceResourceCatalog(List<ObservedSurfaceResource> resources) {
        this.resources = List.copyOf(resources);
        Map<String, ObservedSurfaceResource> byKey = new TreeMap<>();
        for (ObservedSurfaceResource resource : this.resources) {
            byKey.put(resource.candidate().qualifiedResourceKey(), resource);
        }
        resourcesByKey = Collections.unmodifiableMap(byKey);
    }

    public List<ObservedSurfaceResource> resources() {
        return resources;
    }

    public Optional<ObservedSurfaceResource> findByQualifiedResourceKey(
            String qualifiedResourceKey
    ) {
        if (qualifiedResourceKey == null || qualifiedResourceKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourcesByKey.get(qualifiedResourceKey));
    }

}
