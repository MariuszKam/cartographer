package cartographer.prospecting;

import cartographer.geology.rock.RockIdentity;

import java.util.Objects;

@FunctionalInterface
public interface OreRockCompatibilityProvider {
    OreRockCompatibility compatibility(
            String resourceKey,
            RockIdentity rock
    );

    static OreRockCompatibilityProvider unknown() {
        return (resourceKey, rock) -> {
            Objects.requireNonNull(resourceKey, "resourceKey is required");
            Objects.requireNonNull(rock, "rock is required");
            return OreRockCompatibility.UNKNOWN;
        };
    }
}
