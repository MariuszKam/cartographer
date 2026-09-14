package cartographer.ui.workstation;

import cartographer.resource.ObservedSurfaceResource;

import java.util.List;
import java.util.Optional;

/** Pure selection policy for the observed surface-resource combo box. */
final class ObservedSurfaceResourceSelection {
    private ObservedSurfaceResourceSelection() {
    }

    static Optional<ObservedSurfaceResource> preserve(
            String previousQualifiedKey,
            List<ObservedSurfaceResource> resources
    ) {
        return resources.stream()
                .filter(resource -> resource.candidate().qualifiedResourceKey()
                        .equals(previousQualifiedKey))
                .findFirst()
                .or(() -> resources.stream().findFirst());
    }

    static String displayName(ObservedSurfaceResource resource) {
        String name = resource.candidate().displayName();
        String namespace = resource.candidate().namespace();
        return "game".equals(namespace) || namespace.isBlank()
                ? name
                : name + " [" + namespace + "]";
    }
}
