package cartographer.resource;

import cartographer.model.BlockInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record SurfaceMaterialMatch(String displayName, List<String> requiredTokens) {
    public SurfaceMaterialMatch {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Surface material display name is required");
        }
        requiredTokens = List.copyOf(Objects.requireNonNull(requiredTokens, "material tokens are required"))
                .stream().map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank()).toList();
        if (requiredTokens.isEmpty()) {
            throw new IllegalArgumentException("Surface material requires at least one token");
        }
    }

    public boolean matches(BlockInfo blockInfo) {
        if (blockInfo == null || blockInfo.code() == null) return false;
        String code = blockInfo.code().toLowerCase(Locale.ROOT);
        return requiredTokens.stream().allMatch(code::contains);
    }

}
