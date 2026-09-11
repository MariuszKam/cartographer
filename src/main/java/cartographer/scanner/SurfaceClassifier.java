package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;

import java.util.Locale;

public class SurfaceClassifier {

    public SurfaceClass classify(
            BlockInfo block,
            BlockInfo liquid
    ) {
        if (isWater(liquid)) {
            return SurfaceClass.WATER;
        }

        if (block == null
                || block.code() == null
                || block.code().startsWith("unknown:")) {

            return SurfaceClass.UNKNOWN;
        }

        if (isWater(block)) {
            return SurfaceClass.WATER;
        }

        String code =
                block.code()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (code.contains("snow")) {
            return SurfaceClass.SNOW;
        }

        if (code.contains("forestfloor")) {
            return SurfaceClass.FOREST_FLOOR;
        }

        if (block.isFoliage()) {
            return SurfaceClass.VEGETATION;
        }

        /*
         * Tall grass and similar vegetation has already been caught
         * by BlockInfo.isFoliage(). What remains here represents
         * grass-like ground surfaces.
         */
        if (code.contains("grass")) {
            return SurfaceClass.GRASS;
        }

        /*
         * Rock must be checked before sand.
         *
         * Example:
         * rock-sandstone
         *
         * contains "sand", but it is rock.
         */
        if (isRockLike(code)) {
            return SurfaceClass.ROCK;
        }

        if (code.contains("gravel")) {
            return SurfaceClass.GRAVEL;
        }

        if (code.contains("sand")) {
            return SurfaceClass.SAND;
        }

        if (containsAny(
                code,
                "soil",
                "clay",
                "peat",
                "mud"
        )) {
            return SurfaceClass.SOIL;
        }

        return SurfaceClass.UNKNOWN;
    }

    private boolean isWater(
            BlockInfo block
    ) {
        if (block == null
                || block.id() == 0
                || block.code() == null) {

            return false;
        }

        return block.code()
                .toLowerCase(
                        Locale.ROOT
                )
                .contains("water");
    }

    private boolean isRockLike(
            String code
    ) {
        return containsAny(
                code,
                "rock",
                "stone",
                "ore",
                "flint",
                "stalag",
                "stalact",
                "looseboulder"
        );
    }

    private boolean containsAny(
            String value,
            String... fragments
    ) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }

        return false;
    }
}