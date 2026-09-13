package cartographer.application;

import cartographer.model.SurfaceBlock;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record SurfaceResourceMatch(
        String displayName,
        List<String> requiredTokens
) {

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

        if (requiredTokens.isEmpty()) {
            throw new IllegalArgumentException("Surface resource requires at least one token");
        }
    }

    public boolean matches(SurfaceBlock block) {
        if (block == null || block.blockInfo() == null || block.blockInfo().code() == null) {
            return false;
        }

        String code = block.blockInfo().code().toLowerCase(Locale.ROOT);
        return requiredTokens.stream().allMatch(code::contains);
    }

    public List<SurfaceBlock> matchingBlocks(List<SurfaceBlock> blocks) {
        return blocks.stream().filter(this::matches).toList();
    }
}
