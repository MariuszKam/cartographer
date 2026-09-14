package cartographer.resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Pure key-based selection operations for the observed Surface Object checklist. */
public final class SurfaceObjectSelectionActions {
    private SurfaceObjectSelectionActions() {
    }

    public static Set<String> selectVisible(
            Collection<String> selectedKeys,
            Collection<ObservedSurfaceResource> visibleResources
    ) {
        TreeSet<String> result = keys(selectedKeys);
        visibleResources.forEach(resource -> result.add(resource.candidate().qualifiedResourceKey()));
        return immutable(result);
    }

    public static Set<String> clearVisible(
            Collection<String> selectedKeys,
            Collection<ObservedSurfaceResource> visibleResources
    ) {
        TreeSet<String> result = keys(selectedKeys);
        visibleResources.forEach(resource -> result.remove(resource.candidate().qualifiedResourceKey()));
        return immutable(result);
    }

    public static Set<String> clearAll() {
        return Set.of();
    }

    private static TreeSet<String> keys(Collection<String> selectedKeys) {
        TreeSet<String> result = new TreeSet<>();
        selectedKeys.forEach(key -> {
            if (key != null) {
                result.add(key);
            }
        });
        return result;
    }

    private static Set<String> immutable(TreeSet<String> keys) {
        return Collections.unmodifiableSet(keys);
    }
}
