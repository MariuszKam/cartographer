package cartographer.application;

import cartographer.model.SurfaceBlock;
import cartographer.model.BlockInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;

public record SurfaceResourceMatch(
        String displayName,
        List<String> requiredTokens,
        List<String> acceptedCodePrefixes
) {

    public SurfaceResourceMatch(
            String displayName,
            List<String> requiredTokens
    ) {
        this(displayName, requiredTokens, List.of());
    }

    public SurfaceResourceMatch {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Surface resource display name is required");
        }

        requiredTokens = List.copyOf(Objects.requireNonNull(
                requiredTokens,
                "Surface resource tokens are required"
        )).stream()
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .toList();
        acceptedCodePrefixes = List.copyOf(Objects.requireNonNull(
                acceptedCodePrefixes,
                "Surface resource code prefixes are required"
        )).stream()
                .map(prefix -> prefix.trim().toLowerCase(Locale.ROOT))
                .filter(prefix -> !prefix.isBlank())
                .toList();

        if (requiredTokens.isEmpty() && acceptedCodePrefixes.isEmpty()) {
            throw new IllegalArgumentException("Surface resource requires at least one token");
        }
    }

    public boolean matches(SurfaceBlock block) {
        return block != null && matches(block.blockInfo());
    }

    public boolean matches(BlockInfo blockInfo) {
        if (blockInfo == null || blockInfo.code() == null) {
            return false;
        }

        String code = blockInfo.code().toLowerCase(Locale.ROOT);
        if (!acceptedCodePrefixes.isEmpty()) {
            int separator = code.indexOf(':');
            String path = separator >= 0 ? code.substring(separator + 1) : code;
            return acceptedCodePrefixes.stream().anyMatch(path::startsWith);
        }
        return requiredTokens.stream().allMatch(code::contains);
    }

    public List<SurfaceBlock> matchingBlocks(List<SurfaceBlock> blocks) {
        return blocks.stream().filter(this::matches).toList();
    }

    public boolean usesSurfaceObjectScan() {
        return !acceptedCodePrefixes.isEmpty();
    }

    public int[] matchingBlockIds(Map<Integer, cartographer.model.BlockInfo> registry) {
        Objects.requireNonNull(registry, "registry is required");
        return registry.values().stream()
                .filter(this::matches)
                .mapToInt(cartographer.model.BlockInfo::id)
                .distinct()
                .sorted()
                .toArray();
    }

    public static SurfaceResourceMatch looseObsidian() {
        return new SurfaceResourceMatch(
                "Obsidian (surface)",
                List.of(),
                List.of(
                        "loosestones-obsidian-",
                        "looseflints-obsidian-",
                        "looseboulders-obsidian-"
                )
        );
    }
}
