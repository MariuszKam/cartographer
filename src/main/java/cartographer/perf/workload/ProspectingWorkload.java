package cartographer.perf.workload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ProspectingWorkload(
        RadiusProfile radius,
        ProspectingStrategy strategy,
        List<ResourceIdentity> selectedResources
) implements WorkloadSpec {
    public ProspectingWorkload {
        Objects.requireNonNull(radius, "radius is required");
        Objects.requireNonNull(strategy, "strategy is required");

        Objects.requireNonNull(selectedResources, "selectedResources is required");
        List<ResourceIdentity> canonical = new ArrayList<>(selectedResources.size());
        for (ResourceIdentity resource : selectedResources) {
            canonical.add(Objects.requireNonNull(resource, "selected resource is required"));
        }
        canonical.sort(Comparator.comparing(ResourceIdentity::value));
        for (int index = 1; index < canonical.size(); index++) {
            if (canonical.get(index - 1).equals(canonical.get(index))) {
                throw new IllegalArgumentException(
                        "selectedResources must not contain duplicates"
                );
            }
        }

        if (strategy == ProspectingStrategy.SELECTED_RESOURCE_SET
                && canonical.isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedResources must not be empty for SELECTED_RESOURCE_SET"
            );
        }
        if (strategy == ProspectingStrategy.ALL_DISCOVERED_SUPPORTED_RESOURCES
                && !canonical.isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedResources must be empty for ALL_DISCOVERED_SUPPORTED_RESOURCES"
            );
        }
        selectedResources = List.copyOf(canonical);
    }

    @Override
    public WorkloadFamily family() {
        return strategy == ProspectingStrategy.SELECTED_RESOURCE_SET
                ? WorkloadFamily.PROSPECTING_SMALL
                : WorkloadFamily.PROSPECTING_FULL;
    }

    @Override
    public String id() {
        if (strategy == ProspectingStrategy.SELECTED_RESOURCE_SET) {
            return family().name() + "_R" + radius.blocks()
                    + "_RS_" + selectedResourcesFingerprint();
        }
        return family().name() + "_R" + radius.blocks();
    }

    private String selectedResourcesFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ResourceIdentity resource : selectedResources) {
                byte[] value = resource.value().getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(value.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(value);
                digest.update((byte) '\n');
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        char[] digits = "0123456789abcdef".toCharArray();
        for (byte value : bytes) {
            result.append(digits[(value >>> 4) & 0x0f]);
            result.append(digits[value & 0x0f]);
        }
        return result.toString();
    }
}
