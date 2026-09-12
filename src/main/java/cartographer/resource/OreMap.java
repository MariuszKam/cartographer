package cartographer.resource;

import cartographer.model.IntDataMap2D;

public record OreMap(
        String resourceKey,
        IntDataMap2D values
) {
    public OreMap {
        if (resourceKey == null
                || resourceKey.isBlank()) {

            throw new IllegalArgumentException(
                    "OreMap resource key is required"
            );
        }

        if (values == null) {
            throw new IllegalArgumentException(
                    "OreMap values are required"
            );
        }
    }
}
