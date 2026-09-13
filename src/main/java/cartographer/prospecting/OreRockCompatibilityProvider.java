package cartographer.prospecting;

import cartographer.geology.rock.RockIdentity;

@FunctionalInterface
public interface OreRockCompatibilityProvider {
    OreRockCompatibility compatibility(
            String resourceKey,
            RockIdentity rock
    );

    static OreRockCompatibilityProvider unknown() {
        return (resourceKey, rock) -> OreRockCompatibility.UNKNOWN;
    }
}
