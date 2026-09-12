package cartographer.ui;

import cartographer.resource.ResourceAnalyzer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ResourceCatalogService {

    private final VcdbsReader reader;
    private final ResourceAnalyzer resourceAnalyzer;

    public ResourceCatalogService(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.resourceAnalyzer = Objects.requireNonNull(
                resourceAnalyzer,
                "resourceAnalyzer is required"
        );
    }

    public List<OreResource> discover(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");

        return resourcesFromKeys(
                resourceAnalyzer.resourceKeys(
                        reader.readMapRegions(
                                savePath,
                                new ReadDiagnostics()
                        )
                )
        );
    }

    static List<OreResource> resourcesFromKeys(List<String> sourceKeys) {
        Objects.requireNonNull(sourceKeys, "sourceKeys are required");

        Map<String, OreResource> resources = new LinkedHashMap<>();

        for (String sourceKey : sourceKeys) {
            if (sourceKey == null || sourceKey.isBlank()) {
                continue;
            }

            String match = shortName(sourceKey);
            if (match.isBlank()) {
                continue;
            }

            resources.putIfAbsent(
                    match,
                    new OreResource(
                            displayName(match),
                            match,
                            sourceKey
                    )
            );
        }

        List<OreResource> result = new ArrayList<>(resources.values());
        result.sort(
                Comparator.comparing(
                                OreResource::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                OreResource::match
                        )
        );
        return List.copyOf(result);
    }

    private static String shortName(String sourceKey) {
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf(':');
        return separator >= 0
                ? normalized.substring(separator + 1)
                : normalized;
    }

    private static String displayName(String match) {
        return switch (match) {
            case "nativecopper", "copper" -> "Native Copper";
            case "cassiterite" -> "Tin / Cassiterite";
            default -> titleCase(match);
        };
    }

    private static String titleCase(String value) {
        String[] words = value.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }
}
