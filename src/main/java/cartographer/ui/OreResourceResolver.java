package cartographer.ui;

import cartographer.model.BlockInfo;
import cartographer.scanner.OreCodeMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class OreResourceResolver {

    public List<OreResource> resolve(
            List<String> sourceKeys,
            Map<Integer, BlockInfo> registry
    ) {
        Objects.requireNonNull(sourceKeys, "sourceKeys are required");
        Objects.requireNonNull(registry, "registry is required");

        Map<String, OreResource> resources = new LinkedHashMap<>();
        for (String sourceKey : sourceKeys) {
            if (sourceKey == null || sourceKey.isBlank()) {
                continue;
            }

            String shortName = shortName(sourceKey);
            if (shortName.isBlank()) {
                continue;
            }

            String match = resolvedMatch(shortName);
            Set<String> matchedCodes = registry.values()
                    .stream()
                    .map(BlockInfo::code)
                    .filter(Objects::nonNull)
                    .map(code -> code.toLowerCase(Locale.ROOT))
                    .filter(code -> OreCodeMatcher.matchesOreCode(code, match))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

            resources.putIfAbsent(
                    match,
                    new OreResource(
                            displayName(shortName),
                            match,
                            sourceKey,
                            !matchedCodes.isEmpty(),
                            matchedCodes.size()
                    )
            );
        }

        List<OreResource> result = new ArrayList<>(resources.values());
        result.sort(
                Comparator.comparing(
                                OreResource::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(OreResource::match)
        );
        return List.copyOf(result);
    }

    public OreResource resolve(
            String sourceKey,
            Collection<BlockInfo> registry
    ) {
        Objects.requireNonNull(registry, "registry is required");
        return resolve(
                List.of(sourceKey),
                registry.stream().collect(
                        java.util.stream.Collectors.toMap(
                                BlockInfo::id,
                                block -> block,
                                (left, right) -> left
                        )
                )
        ).getFirst();
    }

    private String resolvedMatch(String shortName) {
        return switch (shortName) {
            case "copper", "nativecopper" -> "nativecopper";
            default -> shortName;
        };
    }

    private String shortName(String sourceKey) {
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf(':');
        return separator >= 0
                ? normalized.substring(separator + 1)
                : normalized;
    }

    private String displayName(String shortName) {
        return switch (shortName) {
            case "nativecopper", "copper" -> "Native Copper";
            case "cassiterite" -> "Tin / Cassiterite";
            default -> titleCase(shortName);
        };
    }

    private String titleCase(String value) {
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
